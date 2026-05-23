package com.mhs.player.player.subtitles.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MsoneApiService {
    
    /**
     * Search for subtitles.
     * Endpoint: https://malayalamsubtitles.org/wp-json/msone-app/v1/search?query={movie_name}
     */
    @GET("search")
    suspend fun searchSubtitles(
        @Query("query") query: String
    ): Response<List<MsoneSearchResponse>>

    /**
     * Get full details for a release.
     * Endpoint: https://malayalamsubtitles.org/wp-json/msone-app/v1/releases/post/{id}
     */
    @GET("releases/post/{id}")
    suspend fun getReleaseDetails(
        @Path("id") id: Long
    ): Response<MsoneReleaseResponse>

    companion object {
        const val BASE_URL = "https://malayalamsubtitles.org/wp-json/msone-app/v1/"
    }
}
