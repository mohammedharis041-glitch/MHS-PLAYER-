package com.mhs.player.player.subtitles.providers

import android.util.Log
import com.mhs.player.BuildConfig
import com.mhs.player.player.subtitles.SubtitleProvider
import com.mhs.player.player.subtitles.api.MsoneApiService
import com.mhs.player.player.subtitles.model.SubtitleResult
import com.mhs.player.player.subtitles.SubtitleDownloader
import com.mhs.player.player.utils.SubtitleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MsoneProvider @Inject constructor(
    private val msoneApi: MsoneApiService,
    private val downloader: SubtitleDownloader
) : SubtitleProvider {
    
    override val providerId: String = "msone"

    private inline fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("MHSPlayer-Subtitles", message)
        }
    }

    override suspend fun search(query: String, languageCode: String, apiKey: String?): List<SubtitleResult> {
        val startTime = System.currentTimeMillis()
        debugLog("MSone search: query='$query', lang='$languageCode'")
        
        return try {
            val searchResponse = msoneApi.searchSubtitles(query)
            
            if (searchResponse.isSuccessful) {
                val searchItems = searchResponse.body()
                val searchCount = searchItems?.size ?: 0
                debugLog("MSone search returned $searchCount raw items")
                
                if (searchItems == null) {
                    debugLog("MSone search response body was null")
                    return emptyList()
                }

                val results = searchItems.take(10).mapNotNull { searchItem ->
                    try {
                        val detailResponse = msoneApi.getReleaseDetails(searchItem.id)
                        
                        if (detailResponse.isSuccessful) {
                            val release = detailResponse.body()
                            if (release == null) {
                                debugLog("MSone detail body null for id ${searchItem.id}")
                                return@mapNotNull null
                            }
                            
                            val pkgId = release.packageId
                            val finalPkgId = if (pkgId.isNullOrBlank()) {
                                debugLog("MSone missing package_id for id ${searchItem.id}, using fallback")
                                release.id?.toString() ?: searchItem.id.toString()
                            } else pkgId
                            
                            val cleanTitle = SubtitleUtils.stripHtml(release.titleEng ?: searchItem.title ?: "Untitled")
                            val cleanMalTitle = SubtitleUtils.stripHtml(release.titleMal ?: "")
                            
                            val relNumber = release.releaseNumber?.let { "#$it" } ?: ""
                            val relName = listOfNotNull(release.fileName, relNumber).joinToString(" ")
                            
                            SubtitleResult(
                                id = "msone_${release.id}",
                                title = cleanTitle,
                                titleMal = cleanMalTitle,
                                language = "Malayalam",
                                languageCode = "ml",
                                provider = providerId,
                                downloadUrl = finalPkgId,
                                directDownloadLink = if (finalPkgId.startsWith("http")) finalPkgId 
                                                    else "https://malayalamsubtitles.org/?package_id=$finalPkgId",
                                releaseName = relName,
                                translator = release.translators?.joinToString(", ") { SubtitleUtils.stripHtml(it.name ?: "Unknown") } ?: "MSone Team",
                                posterUrl = release.posterRaw ?: release.poster ?: searchItem.poster,
                                rating = release.imdbRating,
                                uploadedAt = release.releaseYear?.toString() ?: "Unknown",
                                downloads = release.downloadCount ?: 0,
                                genres = release.genres?.joinToString(", ") { it.name ?: "" },
                                releaseYear = release.releaseYear,
                                releaseType = release.releaseType?.firstOrNull()?.name
                            )
                        } else {
                            Log.e("MHSPlayer-Subtitles", "MSone detail failed for id ${searchItem.id}: ${detailResponse.code()}")
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("MHSPlayer-Subtitles", "MSone detail parse failed for id ${searchItem.id}", e)
                        null
                    }
                }
                
                debugLog("MSone final results: ${results.size} (${System.currentTimeMillis() - startTime}ms)")
                results
            } else {
                Log.e("MHSPlayer-Subtitles", "MSone search failed: ${searchResponse.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("MHSPlayer-Subtitles", "MSone search exception", e)
            emptyList()
        }
    }

    override suspend fun download(subtitle: SubtitleResult, videoTitle: String, apiKey: String?): File? = withContext(Dispatchers.IO) {
        val url = subtitle.directDownloadLink ?: return@withContext null
        debugLog("Msone: Downloading from $url")
        downloader.download(url, subtitle.id, videoTitle)
    }
}
