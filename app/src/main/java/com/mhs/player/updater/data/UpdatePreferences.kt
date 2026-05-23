package com.mhs.player.updater.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updaterDataStore: DataStore<Preferences> by preferencesDataStore(name = "mhs_updater")

@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SKIPPED_VERSION = stringPreferencesKey("skipped_version")
        val LAST_CHECKED_TIMESTAMP = longPreferencesKey("last_checked")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val AUTO_CHECK = booleanPreferencesKey("auto_check")
        val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download")
    }

    data class UpdaterSettings(
        val skippedVersion: String = "",
        val lastCheckedTimestamp: Long = 0L,
        val updateChannel: String = "stable", // "stable" or "beta"
        val autoCheck: Boolean = true,
        val autoDownload: Boolean = false
    )

    val settings: Flow<UpdaterSettings> = context.updaterDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            UpdaterSettings(
                skippedVersion = prefs[Keys.SKIPPED_VERSION] ?: "",
                lastCheckedTimestamp = prefs[Keys.LAST_CHECKED_TIMESTAMP] ?: 0L,
                updateChannel = prefs[Keys.UPDATE_CHANNEL] ?: "stable",
                autoCheck = prefs[Keys.AUTO_CHECK] ?: true,
                autoDownload = prefs[Keys.AUTO_DOWNLOAD] ?: false
            )
        }

    suspend fun setSkippedVersion(version: String) {
        context.updaterDataStore.edit { prefs ->
            prefs[Keys.SKIPPED_VERSION] = version
        }
    }

    suspend fun setLastCheckedTimestamp(timestamp: Long) {
        context.updaterDataStore.edit { prefs ->
            prefs[Keys.LAST_CHECKED_TIMESTAMP] = timestamp
        }
    }

    suspend fun setUpdateChannel(channel: String) {
        context.updaterDataStore.edit { prefs ->
            prefs[Keys.UPDATE_CHANNEL] = channel
        }
    }

    suspend fun setAutoCheck(enabled: Boolean) {
        context.updaterDataStore.edit { prefs ->
            prefs[Keys.AUTO_CHECK] = enabled
        }
    }

    suspend fun setAutoDownload(enabled: Boolean) {
        context.updaterDataStore.edit { prefs ->
            prefs[Keys.AUTO_DOWNLOAD] = enabled
        }
    }
}
