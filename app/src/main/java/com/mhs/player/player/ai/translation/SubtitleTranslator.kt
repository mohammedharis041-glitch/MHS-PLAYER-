package com.mhs.player.player.ai.translation

import android.content.Context
import androidx.media3.common.text.Cue
import com.mhs.player.core.coroutine.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import com.mhs.player.player.subtitles.parser.SubtitleCue

@Singleton
class SubtitleTranslator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: com.mhs.player.settings.SettingsRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var sourceLang: String = "en"
    private var targetLang: String = "ml"
    
    // Limit cloud API concurrency - increased for smoother pre-translation
    private val apiSemaphore = Semaphore(5)
    
    private var preTranslateJob: Job? = null

    /**
     * Synchronously check if all cues are in the cache.
     */
    fun getCachedTranslation(cues: List<Cue>, target: String): List<Cue>? {
        val texts = cues.mapNotNull { it.text?.toString() }.filter { it.isNotBlank() }
        if (texts.isEmpty()) return null
        
        val allInCache = texts.all { cache.containsKey("${target}_$it") }
        if (!allInCache) return null

        return cues.map { cue ->
            val text = cue.text?.toString() ?: return@map cue
            val translated = cache["${target}_$text"] ?: text
            cue.buildUpon().setText(translated).build()
        }
    }

    suspend fun translateCues(cues: List<Cue>, target: String): List<Cue> = withContext(Dispatchers.IO) {
        if (cues.isEmpty()) return@withContext cues
        logVerbose("MHSPlayer-Subtitles", "Translator: Processing ${cues.size} cues for target: $target")
        this@SubtitleTranslator.targetLang = target
        
        val textsToTranslate = cues.mapNotNull { it.text?.toString()?.trim() }.filter { it.isNotBlank() }.distinct()
        if (textsToTranslate.isEmpty()) {
            logVerbose("MHSPlayer-Subtitles", "Translator: No translatable text found in cues")
            return@withContext cues
        }

        // 1. Detect source language (Placeholder - can be improved with cloud API if needed)
        // detectSourceLanguageIfNeeded(textsToTranslate.first())

        // 2. Check cache
        val cached = getCachedTranslation(cues, target)
        if (cached != null) {
            logVerbose("MHSPlayer-Subtitles", "Translator: All cues found in cache")
            return@withContext cached
        }

        logVerbose("MHSPlayer-Subtitles", "Translator: Cache miss. Sending batch to translation engines...")
        // 3. Translate batch
        val translatedMap = translateBatch(textsToTranslate, target)
        
        cues.map { cue ->
            val text = cue.text?.toString()
            if (text == null || text.isBlank()) {
                cue
            } else {
                val trimmedText = text.trim()
                val translated = translatedMap[trimmedText] ?: text
                cue.buildUpon().setText(translated).build()
            }
        }
    }

    private suspend fun translateBatch(texts: List<String>, target: String): Map<String, String> = coroutineScope {
        val settings = settingsRepository.settings.first()
        val apiKey = settings.subtitleApiKey

        // 1. Try Gemini (Premium AI - BEST QUALITY)
        if (apiKey.isNotBlank()) {
            val geminiResults = tryTranslateWithGemini(texts, target, apiKey)
            if (geminiResults != null) return@coroutineScope geminiResults
        }

        // 2. Try Cloud APIs
        texts.map { text ->
            async {
                val cached = cache["${target}_$text"]
                if (cached != null) return@async text to cached
                
                val translated = apiSemaphore.withPermit {
                    translateTextViaApiChain(text, target)
                }
                
                if (translated != text) {
                    cache["${target}_$text"] = translated
                }
                text to translated
            }
        }.awaitAll().toMap()
    }

    private suspend fun tryTranslateWithGemini(texts: List<String>, target: String, apiKey: String): Map<String, String>? {
        return try {
            apiSemaphore.withPermit {
                logVerbose("MHSPlayer-Subtitles", "Gemini: Translating ${texts.size} items to $target")
                val prompt = """
                    Translate these movie subtitles strictly to $target. 
                    Requirements:
                    - Return ONLY a raw JSON object.
                    - Format: {"original": "translated"}
                    - Each "translated" value MUST be ONLY in $target language.
                    - NEVER include the original English text or any English explanations in the "translated" value.
                    - NO side-by-side (English + $target) translations.
                    - Preserve the emotional tone and context of the movie.
                    
                    Subtitles to translate:
                    ${texts.joinToString("\n")}
                """.trimIndent()

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", apiKey)
                conn.doOutput = true
                
                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
                            })
                        })
                    })
                }

                conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }
                
                logVerbose("MHSPlayer-Subtitles", "Gemini: Request sent, waiting for response...")
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val jsonResponse = JSONObject(response)
                    val textResult = jsonResponse.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    
                    logVerbose("MHSPlayer-Subtitles", "Gemini: RAW RESPONSE: $textResult")
                    
                    // Parse the JSON block from LLM response
                    val jsonStart = textResult.indexOf("{")
                    val jsonEnd = textResult.lastIndexOf("}") + 1
                    if (jsonStart != -1 && jsonEnd != -1) {
                        val pureJson = textResult.substring(jsonStart, jsonEnd)
                        val mapping = JSONObject(pureJson)
                        val result = mutableMapOf<String, String>()
                        mapping.keys().forEach { key ->
                            val value = mapping.getString(key)
                            val trimmedKey = key.trim()
                            cache["${target}_$trimmedKey"] = value
                            result[trimmedKey] = value
                        }
                        logVerbose("MHSPlayer-Subtitles", "Gemini: Successfully parsed ${result.size} translations")
                        result
                    } else {
                        Log.e("MHSPlayer-Subtitles", "Gemini: Failed to find JSON block in response")
                        null
                    }
                } else {
                    val error = conn.errorStream?.bufferedReader()?.readText()
                    Log.e("MHSPlayer-Subtitles", "Gemini: API Error ${conn.responseCode}: $error")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("MHSPlayer-Subtitles", "Gemini: Exception during translation", e)
            null
        }
    }

    private suspend fun translateTextViaApiChain(text: String, target: String): String {
        val google = translateTextViaGoogle(text, target)
        if (google != text) return google
        
        val lingva = translateTextViaLingva(text, target)
        if (lingva != text) return lingva
        
        return text
    }

    private suspend fun translateTextViaGoogle(text: String, target: String): String {
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t&q=$encodedText")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36")
            conn.connectTimeout = 3000
            val response = conn.inputStream.bufferedReader().readText()
            parseGoogleResponse(response, text)
        } catch (e: Exception) {
            text
        }
    }

    private suspend fun translateTextViaLingva(text: String, target: String): String {
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
            val url = URL("https://lingva.ml/api/v1/auto/$target/$encodedText")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/112.0.0.0 Mobile Safari/537.36")
            conn.connectTimeout = 3000
            val response = conn.inputStream.bufferedReader().readText()
            val json = org.json.JSONObject(response)
            json.optString("translation", text)
        } catch (e: Exception) {
            text
        }
    }

    private fun parseGoogleResponse(response: String, originalText: String): String {
        return try {
            val jsonArray = JSONArray(response)
            val translatedText = StringBuilder()
            val segments = jsonArray.optJSONArray(0)
            if (segments != null) {
                for (i in 0 until segments.length()) {
                    val segment = segments.optJSONArray(i)
                    val textPart = segment?.optString(0)
                    if (textPart != null && textPart != "null") {
                        translatedText.append(textPart)
                    }
                }
            }
            if (translatedText.isNotEmpty()) translatedText.toString() else originalText
        } catch (e: Exception) {
            originalText
        }
    }

    fun startPreTranslation(cues: List<SubtitleCue>, target: String, startPosMs: Long) {
        preTranslateJob?.cancel()
        preTranslateJob = applicationScope.launch(Dispatchers.IO) {
            val upcoming = cues.filter { it.startTimeMs >= startPosMs }
            // Larger chunks (20 lines) to stay ahead of playback
            upcoming.chunked(20).forEach { window ->
                val texts = window.map { it.text }.filter { !cache.containsKey("${target}_$it") }
                if (texts.isNotEmpty()) {
                    translateBatch(texts, target)
                    // Moderate delay to stay ahead without overwhelming the API
                    delay(1200)
                }
            }
        }
    }

    fun clearPreTranslation() {
        preTranslateJob?.cancel()
        preTranslateJob = null
    }

    fun clearCache() {
        cache.clear()
    }

    private inline fun logVerbose(tag: String, msg: String) {
        if (com.mhs.player.BuildConfig.DEBUG) {
            Log.v(tag, msg)
        }
    }
}
