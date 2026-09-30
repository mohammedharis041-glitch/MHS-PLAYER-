package com.mhs.player.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * v2-beta: Per-video settings memory
 * Remembers audio track, subtitle, speed per video
 */
@Entity(tableName = "video_settings")
data class VideoSettings(
    @PrimaryKey val videoId: String, // media URI hash or path
    val audioTrackIndex: Int = -1,
    val subtitleTrackId: String? = null,
    val subtitleEnabled: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val lastUpdated: Long = System.currentTimeMillis()
)
