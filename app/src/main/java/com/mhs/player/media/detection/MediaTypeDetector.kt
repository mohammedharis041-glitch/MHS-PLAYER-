package com.mhs.player.media.detection

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaTypeDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "flv", "ts", "m4v",
        "3gp", "3g2", "rmvb", "wmv", "asf", "divx", "m2ts", "mts"
    )

    private val audioExtensions = setOf(
        "mp3", "flac", "aac", "ogg", "wav", "m4a", "wma", "opus",
        "alac", "aiff", "ape", "mka"
    )

    fun isVideo(uri: Uri): Boolean {
        val mimeType = getMimeType(uri)
        if (mimeType != null) return mimeType.startsWith("video/")
        val ext = getExtension(uri.toString())
        return ext in videoExtensions
    }

    fun isAudio(uri: Uri): Boolean {
        val mimeType = getMimeType(uri)
        if (mimeType != null) return mimeType.startsWith("audio/")
        val ext = getExtension(uri.toString())
        return ext in audioExtensions
    }

    fun isVideo(path: String): Boolean {
        val ext = getExtension(path)
        return ext in videoExtensions
    }

    fun isAudio(path: String): Boolean {
        val ext = getExtension(path)
        return ext in audioExtensions
    }

    private fun getMimeType(uri: Uri): String? =
        context.contentResolver.getType(uri)

    private fun getExtension(path: String): String =
        MimeTypeMap.getFileExtensionFromUrl(path)?.lowercase()
            ?: path.substringAfterLast(".", "").lowercase()
}
