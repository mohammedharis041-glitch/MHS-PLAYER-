package com.mhs.player.player.subtitles

import com.mhs.player.player.subtitles.model.SubtitleResult
import java.io.File

/**
 * Interface defining the contract for any subtitle provider.
 */
interface SubtitleProvider {
    /** Unique identifier for the provider. */
    val providerId: String
    
    /** Search for subtitles based on a query. */
    suspend fun search(query: String, languageCode: String, apiKey: String? = null): List<SubtitleResult>
    
    /** Download a specific subtitle. */
    suspend fun download(subtitle: SubtitleResult, videoTitle: String, apiKey: String? = null): File?
}
