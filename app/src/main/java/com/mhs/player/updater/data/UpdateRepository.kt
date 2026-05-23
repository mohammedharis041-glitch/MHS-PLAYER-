package com.mhs.player.updater.data

import android.util.Log
import com.mhs.player.updater.api.GitHubApi
import com.mhs.player.updater.api.models.UpdateManifest
import com.mhs.player.updater.utils.UpdateConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MHSUpdater-Repo"
private const val MANIFEST_URL = "https://raw.githubusercontent.com/mohammedharis041-glitch/MHS-PLAYER-/main/update.json"

@Singleton
class UpdateRepository @Inject constructor(
    private val gitHubApi: GitHubApi,
    private val updatePreferences: UpdatePreferences
) {
    val preferences = updatePreferences

    /**
     * Fetches the latest update manifest from the raw GitHub JSON URL.
     * Lightweight, no authentication, bypasses rate limiting completely.
     */
    suspend fun getLatestUpdate(channel: String = "stable"): UpdateManifest? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching update manifest from URL: $MANIFEST_URL")

            val response = gitHubApi.getUpdateManifest(MANIFEST_URL)
            val finalUrl = response.raw().request.url
            Log.d(TAG, "Final API URL: $finalUrl")
            Log.d(TAG, "Response Code: HTTP ${response.code()}")

            if (response.isSuccessful) {
                val manifest = response.body()
                if (manifest != null) {
                    Log.d(TAG, "Manifest fetched successfully:")
                    Log.d(TAG, "  versionCode = ${manifest.versionCode}")
                    Log.d(TAG, "  versionName = ${manifest.versionName}")
                    Log.d(TAG, "  apkUrl      = ${manifest.apkUrl}")
                    Log.d(TAG, "  mandatory   = ${manifest.mandatory}")
                    Log.d(TAG, "  changelog   = ${manifest.changelog}")
                    return@withContext manifest
                } else {
                    Log.w(TAG, "Manifest was empty/null despite HTTP 200")
                }
            } else {
                Log.e(TAG, "Failed to fetch manifest: HTTP ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching manifest: ${e.javaClass.simpleName}: ${e.message}", e)
        }
        return@withContext null
    }
}
