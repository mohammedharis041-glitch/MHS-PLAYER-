package com.mhs.player.media.filesystem

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun resolveUri(mediaId: Long, isVideo: Boolean): Uri {
        return if (isVideo) {
            android.content.ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId
            )
        } else {
            android.content.ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId
            )
        }
    }

    fun fileExists(path: String): Boolean = File(path).exists()

    fun getFileExtension(path: String): String =
        File(path).extension.lowercase()

    fun getMimeTypeFromExtension(ext: String): String = when (ext) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "flv" -> "video/x-flv"
        "ts" -> "video/mp2ts"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        else -> "*/*"
    }
}

@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getExternalStoragePaths(): List<String> {
        return context.getExternalFilesDirs(null)
            .filterNotNull()
            .map { it.absolutePath.substringBefore("/Android") }
            .filter { it.isNotBlank() }
    }

    fun getAvailableStorage(): Long {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }
}

@Singleton
class MediaIndexer @Inject constructor() {
    private val indexedPaths = mutableSetOf<String>()

    fun isIndexed(path: String): Boolean = indexedPaths.contains(path)

    fun markIndexed(path: String) { indexedPaths.add(path) }

    fun clearIndex() { indexedPaths.clear() }
}

@Singleton
class FolderTreeBuilder @Inject constructor() {
    data class FolderNode(
        val path: String,
        val name: String,
        val children: MutableList<FolderNode> = mutableListOf(),
        var mediaCount: Int = 0
    )

    fun buildTree(paths: List<String>): List<FolderNode> {
        val root = mutableListOf<FolderNode>()
        val nodeMap = mutableMapOf<String, FolderNode>()

        paths.sorted().forEach { path ->
            val parts = path.split("/").filter { it.isNotBlank() }
            var current = root
            var currentPath = ""
            parts.forEachIndexed { index, part ->
                currentPath += "/$part"
                val existing = nodeMap[currentPath]
                if (existing != null) {
                    current = existing.children
                } else {
                    val node = FolderNode(currentPath, part)
                    nodeMap[currentPath] = node
                    (if (index == 0) root else current).add(node)
                    current = node.children
                }
            }
        }
        return root
    }
}
