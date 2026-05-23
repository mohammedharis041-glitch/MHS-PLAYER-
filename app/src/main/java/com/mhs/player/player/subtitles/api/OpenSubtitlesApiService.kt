package com.mhs.player.player.subtitles.api

import retrofit2.Response
import retrofit2.http.*

interface OpenSubtitlesApiService {
    
    @GET("subtitles")
    suspend fun search(
        @Query("query") query: String,
        @Query("languages") languages: String,
        @Query("order_by") orderBy: String = "download_count",
        @Query("order_direction") orderDirection: String = "desc",
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String
    ): Response<OpenSubtitlesResponse>

    @POST("download")
    suspend fun getDownloadLink(
        @Body body: Map<String, Int>,
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String
    ): Response<DownloadLinkResponse>
}

data class OpenSubtitlesResponse(
    val data: List<SubtitleData>
)

data class SubtitleData(
    val id: String,
    val attributes: SubtitleAttributes
)

data class SubtitleAttributes(
    val release: String,
    val language: String,
    val upload_date: String,
    val download_count: Int,
    val fps: Double,
    val language_info: LanguageInfo?,
    val files: List<SubtitleFile>
)

data class LanguageInfo(
    val name: String
)

data class SubtitleFile(
    val file_id: Int
)

data class DownloadLinkResponse(
    val link: String
)
