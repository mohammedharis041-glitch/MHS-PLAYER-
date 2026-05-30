package com.mhs.player.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "playback_history",
    indices = [Index(value = ["mediaId"], unique = true)]
)
data class PlaybackHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val title: String,
    val path: String,
    val uri: String,
    val duration: Long,
    val lastPosition: Long = 0L,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 1,
    val thumbnailPath: String? = null,
    val mediaType: String = "VIDEO",
    val subtitleDelay: Long = 0L,
    val subtitleSpeed: Float = 1.0f,
    val subtitlePath: String? = null,
    val audioDelay: Long = 0L,
    val audioTrackIndex: Int = -1
)

@Entity(
    tableName = "favorites",
    indices = [Index(value = ["mediaId"], unique = true)]
)
data class FavoriteItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val title: String,
    val path: String,
    val uri: String,
    val duration: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null,
    val mediaType: String = "VIDEO"
)
