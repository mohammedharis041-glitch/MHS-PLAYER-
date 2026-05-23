package com.mhs.player.player.subtitles.providers

import android.util.Log
import androidx.core.text.HtmlCompat
import com.mhs.player.player.subtitles.SubtitleProvider
import com.mhs.player.player.subtitles.model.SubtitleResult
import com.mhs.player.player.subtitles.SubtitleDownloader
import com.mhs.player.player.utils.SubtitleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleCatProvider @Inject constructor(
    private val downloader: SubtitleDownloader
) : SubtitleProvider {
    
    override val providerId: String = "subtitlecat"

    override suspend fun search(query: String, languageCode: String, apiKey: String?): List<SubtitleResult> = withContext(Dispatchers.IO) {
        try {
            // Map ISO codes to SubtitleCat language names (normalization)
            val catLang = when(languageCode.lowercase()) {
                "ml", "mal", "malayalam" -> "malayalam"
                "ta", "tam", "tamil" -> "tamil"
                "hi", "hin", "hindi" -> "hindi"
                "te", "tel", "telugu" -> "telugu"
                "kn", "kan", "kannada" -> "kannada"
                "ar", "ara", "arabic" -> "arabic"
                "en", "eng", "english" -> "english"
                "fr", "fra", "french" -> "french"
                "de", "deu", "german" -> "german"
                "es", "spa", "spanish" -> "spanish"
                "tr", "tur", "turkish" -> "turkish"
                else -> "english"
            }
            
            Log.d("MHSPlayer-Subtitles", "SubtitleCat: Searching - Query: '$query', Lang: '$catLang' (ISO: $languageCode)")
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://subtitlecat.com/index.php?search=$encodedQuery&l=$catLang")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            conn.connectTimeout = 8000
            
            if (conn.responseCode != 200) {
                Log.e("MHSPlayer-Subtitles", "SubtitleCat: Search HTTP failed code ${conn.responseCode}")
                return@withContext emptyList()
            }
            
            val html = conn.inputStream.bufferedReader().readText()
            val results = mutableListOf<SubtitleResult>()
            
            // 1. Isolate the results table
            val tableStart = html.indexOf("<table class=\"table sub-table\">")
            if (tableStart == -1) {
                Log.d("MHSPlayer-Subtitles", "SubtitleCat: No results table in HTML")
                return@withContext emptyList()
            }
            val tableEnd = html.indexOf("</table>", tableStart) + 8
            val tableHtml = html.substring(tableStart, tableEnd)

            // 2. Parse rows
            val rowPattern = Regex("<tr>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
            rowPattern.findAll(tableHtml).forEach { rowMatch ->
                val rowHtml = rowMatch.groupValues[1]
                
                val tdPattern = Regex("<td.*?>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
                val tds = tdPattern.findAll(rowHtml).toList()
                if (tds.isEmpty()) return@forEach

                val mainCell = tds[0].groupValues[1]
                val linkMatch = Regex("<a\\s+href=\"(subs/[^\"]+)\".*?>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).find(mainCell) ?: return@forEach
                
                val path = linkMatch.groupValues[1]
                val rawTitle = linkMatch.groupValues[2]
                val sanitizedTitle = SubtitleUtils.stripHtml(rawTitle).trim()
                
                // 3. Language Detection
                val filename = path.substringAfterLast("/")
                val nameWithoutExtension = filename.substringBeforeLast(".html")
                val suffixMatch = Regex("-([a-z]{2,3})$", RegexOption.IGNORE_CASE).find(nameWithoutExtension)
                val pathCode = suffixMatch?.groupValues?.get(1)?.lowercase() ?: ""
                
                val downloads = if (tds.size >= 4) {
                    Regex("\\d+").find(tds[3].groupValues[1])?.value?.toIntOrNull() ?: 0
                } else 0

                var detectedLang = if (pathCode.isNotEmpty()) {
                    val loc = java.util.Locale(pathCode)
                    val disp = loc.getDisplayLanguage(java.util.Locale.ENGLISH)
                    if (disp.isNotEmpty()) disp.replaceFirstChar { it.uppercase() } else "Unknown"
                } else "Unknown"

                if (detectedLang == "Unknown" || detectedLang == "English") {
                    if (mainCell.contains("translated from", ignoreCase = true)) {
                        detectedLang = catLang.replaceFirstChar { it.uppercase() }
                    }
                }

                // 4. Verification & Soft Relevance
                // Bypass strict language rejection: we accept all results but log details and reasons
                val matchesTarget = pathCode == languageCode || detectedLang.equals(catLang, ignoreCase = true)
                val isEnglish = pathCode == "en" || detectedLang.equals("English", ignoreCase = true)
                
                if (!matchesTarget && !isEnglish) {
                    Log.d("MHSPlayer-Subtitles", "SubtitleCat: Bypassing strict language rejection for '$sanitizedTitle' (Detected: $detectedLang, PathCode: $pathCode, Target: $languageCode)")
                } else {
                    Log.d("MHSPlayer-Subtitles", "SubtitleCat: MATCH - '$sanitizedTitle' ($detectedLang)")
                }
                
                val finalLangCode = if (matchesTarget) languageCode else if (isEnglish) "en" else pathCode
                results.add(
                    SubtitleResult(
                        id = "cat_${path.hashCode()}",
                        title = sanitizedTitle,
                        language = detectedLang,
                        languageCode = finalLangCode,
                        provider = providerId,
                        downloadUrl = "https://subtitlecat.com/${path}",
                        downloads = downloads,
                        uploadedAt = "SubtitleCat"
                    )
                )
            }
            
            Log.d("MHSPlayer-Subtitles", "SubtitleCat: Found ${results.size} filtered results")
            results.distinctBy { it.downloadUrl }
                .sortedByDescending { it.languageCode == languageCode }
        } catch (e: Exception) {
            Log.e("MHSPlayer-Subtitles", "SubtitleCat: Exception during search", e)
            emptyList()
        }
    }

    override suspend fun download(subtitle: SubtitleResult, videoTitle: String, apiKey: String?): File? = withContext(Dispatchers.IO) {
        try {
            val urlStr = subtitle.downloadUrl
            Log.d("MHSPlayer-Subtitles", "SubtitleCat: Downloading details from $urlStr")
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 8000
            
            if (conn.responseCode != 200) {
                Log.e("MHSPlayer-Subtitles", "SubtitleCat: Detail HTTP failed code ${conn.responseCode}")
                return@withContext null
            }
            
            val html = conn.inputStream.bufferedReader().readText()
            Log.d("MHSPlayer-Subtitles", "SubtitleCat: Detail page loaded, length: ${html.length}")

            // Extract the numeric ID from the URL (e.g. /subs/12345/Language.html)
            val idMatch = Regex("/subs/(\\d+)/").find(urlStr)
            val subId = idMatch?.groupValues?.get(1)
            val langCode = subtitle.languageCode.lowercase()
            
            Log.d("MHSPlayer-Subtitles", "SubtitleCat Scraping: ID: '$subId', LangCode: '$langCode', LangName: '${subtitle.language}'")

            // Extract all download URLs from <a> tags, completely independent of attribute order!
            val hrefPattern = Regex("<a[^>]+href=\"([^\"]*(?:download/|/subs/|/download\\.php|\\.srt|\\.vtt|\\.ass)[^\"]*)\"", RegexOption.IGNORE_CASE)
            val allDownloadLinks = hrefPattern.findAll(html).map { it.groupValues[1] }.toList()
            Log.d("MHSPlayer-Subtitles", "SubtitleCat Scraping: Found ${allDownloadLinks.size} download links on page")

            var bestLink: String? = null
            var bestScore = -1

            for (link in allDownloadLinks) {
                var score = 0
                val lowercaseLink = link.lowercase()
                
                val suffixMatch = Regex("-([a-z]{2,3})\\.(?:srt|vtt|ass)$", RegexOption.IGNORE_CASE).find(lowercaseLink)
                val suffixLang = suffixMatch?.groupValues?.get(1)
                
                if (suffixLang != null) {
                    if (suffixLang == langCode) {
                        score += 1000 // Exact requested language match (highest priority)
                    } else if (suffixLang == "en") {
                        score += 200  // English fallback (second priority)
                    } else {
                        score += 10   // Other languages (last resort fallback)
                    }
                } else {
                    // No language suffix (could be the original/default file)
                    score += 50
                }
                
                // Tie-breaker: match the subtitle ID if present
                if (subId != null && lowercaseLink.contains(subId)) {
                    score += 5
                }
                
                // Matches language name (e.g., "malayalam")
                val langName = subtitle.language.lowercase()
                if (langName.isNotEmpty() && lowercaseLink.contains(langName)) {
                    score += 2
                }
                
                if (lowercaseLink.endsWith(".srt")) {
                    score += 1
                }
                
                Log.d("MHSPlayer-Subtitles", "SubtitleCat Scraping: Evaluated '$link' -> Score: $score")
                
                if (score > bestScore) {
                    bestScore = score
                    bestLink = link
                }
            }

            // Ultimate direct URL extension fallback:
            // If bestLink is still null, and the detail URL ends with .html,
            // we can try downloading the original SRT file directly by changing the extension to .srt!
            if (bestLink == null && urlStr.endsWith(".html", ignoreCase = true)) {
                bestLink = urlStr.replace(".html", ".srt", ignoreCase = true)
                Log.d("MHSPlayer-Subtitles", "SubtitleCat Scraping: Fallback to direct SRT URL: $bestLink")
            }

            val finalUrl = bestLink
            if (finalUrl != null) {
                val absoluteUrl = when {
                    finalUrl.startsWith("http") -> finalUrl
                    finalUrl.startsWith("/") -> "https://subtitlecat.com$finalUrl"
                    else -> "https://subtitlecat.com/$finalUrl"
                }
                
                Log.d("MHSPlayer-Subtitles", "SubtitleCat: Final resolved download link: $absoluteUrl")
                return@withContext downloader.download(absoluteUrl, subtitle.id, videoTitle)
            }
            
            Log.e("MHSPlayer-Subtitles", "SubtitleCat: FAILED to extract any download link.")
            null
        } catch (e: Exception) {
            Log.e("MHSPlayer-Subtitles", "SubtitleCat: Download scraping exception", e)
            null
        }
    }
}
