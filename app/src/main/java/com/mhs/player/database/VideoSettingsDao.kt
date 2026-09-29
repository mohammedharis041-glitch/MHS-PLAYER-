package com.mhs.player.database

import androidx.room.*

@Dao
interface VideoSettingsDao {
    @Query("SELECT * FROM video_settings WHERE videoId = :videoId")
    suspend fun getSettings(videoId: String): VideoSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: VideoSettings)

    @Query("DELETE FROM video_settings WHERE videoId = :videoId")
    suspend fun clearSettings(videoId: String)

    @Query("DELETE FROM video_settings")
    suspend fun clearAll()
}
