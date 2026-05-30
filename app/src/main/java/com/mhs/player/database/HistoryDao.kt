package com.mhs.player.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC")
    fun getAllHistory(): Flow<List<PlaybackHistory>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 20): Flow<List<PlaybackHistory>>

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getHistoryForMedia(mediaId: Long): PlaybackHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistory)

    @Query("UPDATE playback_history SET lastPosition = :position, lastPlayedAt = :timestamp, playCount = playCount + 1 WHERE mediaId = :mediaId")
    suspend fun updatePosition(mediaId: Long, position: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playback_history SET subtitleDelay = :delay, subtitleSpeed = :speed WHERE mediaId = :mediaId")
    suspend fun updateSubtitleSync(mediaId: Long, delay: Long, speed: Float)

    @Query("UPDATE playback_history SET subtitleDelay = :delay, audioDelay = :audioDelay WHERE mediaId = :mediaId")
    suspend fun updateSyncDelays(mediaId: Long, delay: Long, audioDelay: Long)

    @Query("UPDATE playback_history SET subtitlePath = :path WHERE mediaId = :mediaId")
    suspend fun updateSubtitlePath(mediaId: Long, path: String?)

    @Query("UPDATE playback_history SET audioTrackIndex = :trackIndex WHERE mediaId = :mediaId")
    suspend fun updateAudioTrack(mediaId: Long, trackIndex: Int)

    @Query("DELETE FROM playback_history WHERE mediaId = :mediaId")
    suspend fun deleteHistory(mediaId: Long)

    @Query("DELETE FROM playback_history")
    suspend fun clearAllHistory()

    @Query("SELECT lastPosition FROM playback_history WHERE mediaId = :mediaId")
    suspend fun getLastPosition(mediaId: Long): Long?
}
