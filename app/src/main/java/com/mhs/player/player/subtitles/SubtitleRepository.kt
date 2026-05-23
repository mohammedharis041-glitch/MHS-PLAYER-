package com.mhs.player.player.subtitles

import android.util.Log
import com.mhs.player.player.subtitles.model.SubtitleResult
import com.mhs.player.player.subtitles.providers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleRepository @Inject constructor(
    private val msoneProvider: MsoneProvider,
    private val openSubtitlesProvider: OpenSubtitlesProvider,
    private val localProvider: LocalSubtitleProvider,
    private val subtitleCatProvider: SubtitleCatProvider
) {

    /**
     * Search across all enabled providers.
     */
    suspend fun search(
        query: String,
        languageCode: String,
        videoPath: String? = null,
        openSubtitlesApiKey: String = ""
    ): List<SubtitleResult> = withContext(Dispatchers.IO) {
        Log.d("MHSPlayer-Subtitles", "Pipeline: Search initiated - Query: '$query', Lang: '$languageCode'")
        val results = mutableListOf<SubtitleResult>()

        // 1. Local Search (High Priority)
        if (videoPath != null) {
            val local = localProvider.search(videoPath, "local")
            Log.d("MHSPlayer-Subtitles", "Pipeline: Local search found ${local.size} files")
            results.addAll(local)
        }

        // 2. Multi-Provider Search
        val deferredResults = listOf(
            async { 
                try {
                    if (languageCode == "ml" || languageCode.startsWith("mal", ignoreCase = true)) {
                        val rawResults = msoneProvider.search(query, languageCode)
                        Log.d("MHSPlayer-Subtitles", "Pipeline: MSone returned ${rawResults.size} raw results")
                        rawResults
                    } else {
                        Log.d("MHSPlayer-Subtitles", "Pipeline: MSone search skipped because target language is not Malayalam ($languageCode)")
                        emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("MHSPlayer-Subtitles", "Pipeline: MSone provider CRASHED", e)
                    emptyList()
                }
            },
            async { 
                try {
                    val rawResults = openSubtitlesProvider.search(query, languageCode, openSubtitlesApiKey)
                    Log.d("MHSPlayer-Subtitles", "Pipeline: OpenSubtitles returned ${rawResults.size} raw results")
                    rawResults
                } catch (e: Exception) {
                    Log.e("MHSPlayer-Subtitles", "Pipeline: OpenSubtitles provider CRASHED", e)
                    emptyList()
                }
            },
            async { 
                try {
                    val rawResults = subtitleCatProvider.search(query, languageCode)
                    Log.d("MHSPlayer-Subtitles", "Pipeline: SubtitleCat returned ${rawResults.size} raw results")
                    rawResults
                } catch (e: Exception) {
                    Log.e("MHSPlayer-Subtitles", "Pipeline: SubtitleCat provider CRASHED", e)
                    emptyList()
                }
            }
        )

        val allResults = deferredResults.awaitAll().flatten()
        results.addAll(allResults)
        
        // Log raw result count before deduplication
        Log.d("MHSPlayer-Subtitles", "Pipeline: Total raw results gathered: ${results.size}")
        
        val finalResults = results.distinctBy { it.id }
        
        Log.d("MHSPlayer-Subtitles", "Pipeline: Finished - Total distinct results displayed: ${finalResults.size}")
        finalResults
    }

    /**
     * Download a subtitle from its provider.
     */
    suspend fun download(
        subtitle: SubtitleResult, 
        videoTitle: String,
        openSubtitlesApiKey: String = ""
    ): File? = withContext(Dispatchers.IO) {
        Log.d("MHSPlayer-Subtitles", "Pipeline: Download started - " +
              "Title: '${subtitle.title}', " +
              "Language: '${subtitle.language}' (${subtitle.languageCode}), " +
              "URL: '${subtitle.downloadUrl}', " +
              "Provider: '${subtitle.provider}', " +
              "ID: '${subtitle.id}'")
        try {
            val file = when (subtitle.provider) {
                "msone" -> msoneProvider.download(subtitle, videoTitle)
                "opensubtitles" -> openSubtitlesProvider.download(subtitle, videoTitle, openSubtitlesApiKey)
                "local" -> localProvider.download(subtitle, videoTitle)
                "subtitlecat" -> subtitleCatProvider.download(subtitle, videoTitle)
                else -> null
            }
            
            if (file != null) {
                Log.d("MHSPlayer-Subtitles", "Pipeline: Download SUCCESS - " +
                      "Filename: '${file.name}', " +
                      "Path: '${file.absolutePath}', " +
                      "Size: ${file.length()} bytes")
            } else {
                Log.e("MHSPlayer-Subtitles", "Pipeline: Download FAILED for '${subtitle.title}'")
            }
            file
        } catch (e: Exception) {
            Log.e("MHSPlayer-Subtitles", "Pipeline: Critical Download Error", e)
            null
        }
    }

    fun extractQueryFromFilename(filename: String): String {
        val baseName = filename.substringBeforeLast(".")
        
        // 1. Identify and preserve season/episode pattern (e.g. S01E02 or S1E2 or 1x02)
        val tvShowPattern = Regex("\\bS(\\d{1,2})[E-x](\\d{1,2})\\b", RegexOption.IGNORE_CASE)
        val tvShowMatch = tvShowPattern.find(baseName)
        var tvInfo = ""
        var workingName = baseName
        if (tvShowMatch != null) {
            tvInfo = tvShowMatch.value
            // Remove the season/episode and everything after it to isolate the show title
            workingName = baseName.substring(0, tvShowMatch.range.first)
        }

        // 2. Identify year pattern (4-digit number between 1900 and 2030)
        val yearPattern = Regex("\\b(19\\d{2}|20[0-2]\\d|2030)\\b")
        val yearMatch = yearPattern.find(workingName)
        var yearInfo = ""
        if (yearMatch != null) {
            yearInfo = yearMatch.value
            // If it's a movie, we can isolate the title by stripping the year and everything after it
            if (tvInfo.isEmpty()) {
                workingName = workingName.substring(0, yearMatch.range.first)
            } else {
                // For TV shows, just strip the year from the title part
                workingName = workingName.replace(yearMatch.value, "")
            }
        }

        // 3. Remove all typical garbage tags and formats
        val garbageRegex = Regex(
            "\\b(720p|1080p|1080i|2160p|4k|hdr|bluray|webrip|hdrip|web-dl|webdl|hdtv|bdrip|dvdrip|brrip|rip|h264|h265|hevc|x264|x265|xvid|divx|10bit|aac|dts|dd5\\.1|dd2\\.0|ddp5\\.1|ddp2\\.0|ac3|dvd|patches|yts|yify|galaxyrg|psa|qxr|tigole|fgt|mx|shaanig|etrg|rarbg|eac3|eng|esub|hgsub|sub|hc|dual-audio|dual\\.audio|multi-audio|multi-language|web|dl|extended|directors\\.cut|directors-cut|unrated|remastered|imax)\\b",
            RegexOption.IGNORE_CASE
        )
        
        val cleanTitle = workingName
            .replace(garbageRegex, " ")
            .replace(Regex("[._\\(\\)\\[\\]\\{\\}\\-\\+\\!]"), " ") // replace common separators with spaces
            .replace(Regex("\\s{2,}"), " ") // collapse multiple spaces
            .trim()

        // 4. Reconstruct clean query
        val cleanQuery = buildString {
            append(cleanTitle)
            if (yearInfo.isNotEmpty()) {
                append(" ")
                append(yearInfo)
            }
            if (tvInfo.isNotEmpty()) {
                append(" ")
                append(tvInfo.uppercase())
            }
        }.trim().replace(Regex("\\s{2,}"), " ")

        Log.d("MHSPlayer-Subtitles", "Normalization - Raw: '$baseName', Normalized: '$cleanQuery' (Title: '$cleanTitle', Year: '$yearInfo', TV: '$tvInfo')")
        
        // Bypass over-normalization: fall back to raw basename if it strips too aggressively (leaving <3 chars)
        return if (cleanQuery.length < 3) baseName else cleanQuery
    }
}
