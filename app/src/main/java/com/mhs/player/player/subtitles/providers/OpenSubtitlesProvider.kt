package com.mhs.player.player.subtitles.providers

import android.content.Context
import android.util.Log
import com.mhs.player.player.subtitles.SubtitleProvider
import com.mhs.player.player.subtitles.api.OpenSubtitlesApiService
import com.mhs.player.player.subtitles.model.SubtitleResult
import com.mhs.player.player.subtitles.SubtitleDownloader
import com.mhs.player.player.utils.SubtitleUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenSubtitlesProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: OpenSubtitlesApiService,
    private val downloader: SubtitleDownloader
) : SubtitleProvider {

    override val providerId: String = "opensubtitles"
    
    private val userAgent = "MHSPlayer v1.0"

    override suspend fun search(query: String, languageCode: String, apiKey: String?): List<SubtitleResult> {
        if (apiKey.isNullOrBlank()) {
            Log.w("MHSPlayer-Subtitles", "OpenSubtitles: Search skipped (No API Key)")
            return emptyList()
        }
        
        Log.d("MHSPlayer-Subtitles", "OpenSubtitles: Search requested - Query: '$query', Lang: '$languageCode'")
        return try {
            val response = api.search(
                query = query.take(100),
                languages = languageCode,
                apiKey = apiKey,
                userAgent = userAgent
            )
            
            if (response.isSuccessful) {
                val data = response.body()?.data ?: return emptyList()
                val results = data.map { item ->
                    val attrs = item.attributes
                    val cleanTitle = SubtitleUtils.stripHtml(attrs.release ?: "Unknown Release").trim()
                    
                    SubtitleResult(
                        id = attrs.files.firstOrNull()?.file_id?.toString() ?: item.id,
                        title = cleanTitle,
                        language = attrs.language_info?.name ?: "Unknown",
                        languageCode = attrs.language,
                        provider = providerId,
                        downloadUrl = attrs.files.firstOrNull()?.file_id?.toString() ?: "",
                        releaseName = SubtitleUtils.stripHtml(attrs.release ?: "").trim(),
                        uploadedAt = attrs.upload_date,
                        downloads = attrs.download_count,
                        fps = attrs.fps
                    )
                }
                Log.d("MHSPlayer-Subtitles", "OpenSubtitles: Found ${results.size} results for '$query'")
                results
            } else {
                Log.e("MHSPlayer-Subtitles", "OpenSubtitles: API error ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("MHSPlayer-Subtitles", "OpenSubtitles: Exception during search", e)
            emptyList()
        }
    }

    override suspend fun download(subtitle: SubtitleResult, videoTitle: String, apiKey: String?): File? = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            Log.e("MHSPlayer-Subtitles", "OpenSubtitles: Download failed (No API Key)")
            return@withContext null
        }
        
        try {
            Log.d("MHSPlayer-Subtitles", "OpenSubtitles: Requesting download link for ID ${subtitle.id}")
            val response = api.getDownloadLink(
                body = mapOf("file_id" to subtitle.id.toInt()),
                apiKey = apiKey,
                userAgent = userAgent
            )
            
            if (response.isSuccessful) {
                val downloadLink = response.body()?.link ?: run {
                    Log.e("MHSPlayer-Subtitles", "OpenSubtitles: Link response successful but null link")
                    return@withContext null
                }
                Log.d("MHSPlayer-Subtitles", "OpenSubtitles: Found link: $downloadLink")
                downloader.download(downloadLink, subtitle.id, videoTitle)
            } else {
                Log.e("MHSPlayer-Subtitles", "OpenSubtitles: Get link failed code ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e("MHSPlayer-Subtitles", "OpenSubtitles: Download exception", e)
            null
        }
    }
}
