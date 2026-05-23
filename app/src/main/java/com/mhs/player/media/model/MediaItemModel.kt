package com.mhs.player.media.model

import android.net.Uri

enum class MediaType { VIDEO, AUDIO }

data class MediaItemModel(
    val id: Long,
    val uri: Uri,
    val title: String,
    val displayName: String,
    val path: String,
    val folderPath: String,
    val folderName: String,
    val duration: Long,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val mimeType: String,
    val mediaType: MediaType,
    val width: Int = 0,
    val height: Int = 0,
    val resolution: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtUri: Uri? = null,
    val isFavorite: Boolean = false,
    val lastPlayedPosition: Long = 0L,
    val playCount: Int = 0
) {
    val formattedDuration: String get() = formatDuration(duration)
    val formattedSize: String get() = formatSize(size)
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
    val isAudio: Boolean get() = mediaType == MediaType.AUDIO

    companion object {
        fun formatDuration(millis: Long): String {
            if (millis <= 0) return "0:00"
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

        fun formatSize(bytes: Long): String {
            return when {
                bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
                bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
                bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }
}

data class FolderModel(
    val path: String,
    val name: String,
    val itemCount: Int,
    val totalDuration: Long,
    val thumbnailUri: Uri?,
    val lastModified: Long,
    val mediaType: MediaType
)

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackSpeed: Float = 1f,
    val repeatMode: Int = 0,
    val shuffleMode: Boolean = false,
    val volume: Float = 1f,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val playWhenReady: Boolean = false,
    val audioDecoderName: String? = null,
    val videoDecoderName: String? = null,
    val isAudioSoftware: Boolean = false,
    val isVideoSoftware: Boolean = false,
    val audioCodec: String? = null,
    val videoCodec: String? = null,
    val subtitleDelay: Long = 0L,
    val audioDelay: Long = 0L,
    val subtitleSpeed: Float = 1.0f
)

data class SubtitleTrack(
    val index: Int,
    val language: String,
    val label: String
)

data class AudioTrack(
    val index: Int,
    val language: String,
    val label: String,
    val channelCount: Int,
    val sampleRate: Int
)

enum class ResizeMode(val label: String, val exoMode: Int) {
    FIT("Fit", 0),
    FIXED_WIDTH("Fixed Width", 1),
    FIXED_HEIGHT("Fixed Height", 2),
    FILL("Fill", 3),
    ZOOM("Zoom", 4),
    TRUE_CROP("True Crop", 5)
}

enum class SortOrder {
    NAME_ASC, NAME_DESC,
    DATE_ASC, DATE_DESC,
    DURATION_ASC, DURATION_DESC,
    SIZE_ASC, SIZE_DESC
}
