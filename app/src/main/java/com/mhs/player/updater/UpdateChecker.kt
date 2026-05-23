package com.mhs.player.updater

import android.util.Log
import com.mhs.player.BuildConfig
import com.mhs.player.updater.api.models.UpdateManifest
import com.mhs.player.updater.data.UpdateRepository
import com.mhs.player.updater.utils.UpdateConstants
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MHSUpdater"

@Singleton
class UpdateChecker @Inject constructor(
    private val repository: UpdateRepository
) {

    data class UpdateResult(
        val hasUpdate: Boolean,
        val latestRelease: UpdateManifest? = null,
        val isForced: Boolean = false,
        val apkUrl: String? = null,
        val apkSize: Long = 0L,
        val changelog: String = "",
        val remoteVersionCode: Int = 0,
        val remoteVersionName: String = ""
    )

    /**
     * Checks if a new update is available.
     *
     * Version comparison strategy:
     *   STRICT: remote.versionCode > local.versionCode
     *
     * @param isManual If true, bypasses the 6-hour rate-limiting cache.
     */
    suspend fun checkForUpdates(isManual: Boolean = false): UpdateResult {
        val prefs = repository.preferences.settings.first()
        val now = System.currentTimeMillis()

        // ── 1. Rate-limit guard (silent checks only) ──────────────────────
        if (!isManual) {
            val lastChecked = prefs.lastCheckedTimestamp
            val elapsed = now - lastChecked
            if (elapsed < UpdateConstants.UPDATE_CHECK_INTERVAL_MS) {
                Log.d(TAG, "Skipping check — last checked ${elapsed / 1000}s ago " +
                        "(interval: ${UpdateConstants.UPDATE_CHECK_INTERVAL_MS / 1000}s)")
                return UpdateResult(hasUpdate = false)
            }
        }

        // ── 2. Save timestamp AFTER confirming we will actually fetch ──────
        repository.preferences.setLastCheckedTimestamp(now)

        // ── 3. Local version info ──────────────────────────────────────────
        val localVersionCode = BuildConfig.VERSION_CODE
        val localVersionName = BuildConfig.VERSION_NAME
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "  Local  → versionCode=$localVersionCode  versionName=$localVersionName")
        Log.d(TAG, "  Manual → $isManual  |  Channel → ${prefs.updateChannel}")
        Log.d(TAG, "═══════════════════════════════════════════")

        // ── 4. Fetch manifest from raw repository ──────────────────────────
        val manifest = repository.getLatestUpdate(prefs.updateChannel)
        if (manifest == null) {
            Log.w(TAG, "Unable to check for updates: manifest was null or fetch failed")
            return UpdateResult(hasUpdate = false)
        }

        // Print manifest JSON to logs
        try {
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            val manifestJson = gson.toJson(manifest)
            Log.d(TAG, "Parsed Manifest JSON:\n$manifestJson")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize manifest to JSON: ${e.message}")
        }

        val remoteVersionCode = manifest.versionCode
        val remoteVersionName = manifest.versionName ?: "1.0.0"
        val apkUrl = manifest.apkUrl

        // ── 5. Strict version comparison ──────────────────────────────────
        val hasNewVersion = remoteVersionCode > localVersionCode

        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "VERSION COMPARISON RESULT:")
        Log.d(TAG, "  Current local versionCode: $localVersionCode")
        Log.d(TAG, "  Remote release versionCode: $remoteVersionCode")
        Log.d(TAG, "  Comparison type: Strict integer (versionCode)")
        Log.d(TAG, "  Result: remote > local = $hasNewVersion")
        Log.d(TAG, "  APK URL: $apkUrl")
        Log.d(TAG, "═══════════════════════════════════════════")

        if (!hasNewVersion) {
            Log.d(TAG, "✅ App is up to date (Update Available Result: false)")
            return UpdateResult(hasUpdate = false)
        }

        Log.d(TAG, "🆕 Update available! (Update Available Result: true) $localVersionName → $remoteVersionName")

        // ── 6. Skipped-version check ───────────────────────────────────────
        if (!isManual && prefs.skippedVersion == remoteVersionName) {
            val isForced = manifest.mandatory
            if (!isForced) {
                Log.d(TAG, "⏭ Version '$remoteVersionName' was previously skipped")
                return UpdateResult(hasUpdate = false)
            }
            Log.d(TAG, "⚠ Forced update — ignoring skipped flag")
        }

        val isForced = manifest.mandatory
        val changelog = manifest.changelog ?: ""

        return UpdateResult(
            hasUpdate            = true,
            latestRelease        = manifest,
            isForced             = isForced,
            apkUrl               = apkUrl,
            apkSize              = manifest.apkSize,
            changelog            = changelog,
            remoteVersionCode    = remoteVersionCode,
            remoteVersionName    = remoteVersionName
        )
    }
}
