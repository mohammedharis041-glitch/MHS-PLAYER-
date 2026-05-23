package com.mhs.player.updater.api

import com.mhs.player.updater.api.models.UpdateManifest
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface GitHubApi {

    @GET
    suspend fun getUpdateManifest(
        @Url url: String
    ): Response<UpdateManifest>
}
