package com.mhs.player.media.imports

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentUriImportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheDir = File(context.cacheDir, "external_media")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    /**
     * Imports the given content URI into the app's cache directory asynchronously.
     * Checks if it's already cached to prevent redundant copies.
     * Reports copy progress.
     */
    suspend fun importUri(uri: Uri, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        // Resolve file name and size
        val (displayName, size) = resolveNameAndSize(uri)
        val sanitizedName = sanitizeFilename(displayName)
        
        // Target file
        val targetFile = File(cacheDir, sanitizedName)

        // Deduplication: If file exists and matches size, use it!
        if (targetFile.exists() && (size == 0L || targetFile.length() == size)) {
            Log.d("ContentUriImportManager", "Cache hit: Using already imported file $sanitizedName")
            onProgress(1.0f)
            return@withContext targetFile
        }

        // Perform safe cleanup of any other cached files before importing to prevent storage accumulation
        cleanupCacheExcept(targetFile)

        Log.d("ContentUriImportManager", "Importing content URI: $uri to $sanitizedName (size: $size bytes)")
        
        // Asynchronous copy
        val inputStream = context.contentResolver.openInputStream(uri) 
            ?: throw IllegalStateException("Could not open input stream for URI: $uri")
        
        val tempFile = File(cacheDir, "temp_import_${System.currentTimeMillis()}_$sanitizedName")
        
        try {
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024) // 64KB Buffer
                    var bytesRead: Int
                    var totalBytesCopied = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesCopied += bytesRead
                        if (size > 0L) {
                            val progress = (totalBytesCopied.toFloat() / size.toFloat()).coerceIn(0f, 0.99f)
                            onProgress(progress)
                        }
                    }
                }
            }
            
            // Rename temp to target file on success
            if (tempFile.renameTo(targetFile)) {
                onProgress(1.0f)
                Log.d("ContentUriImportManager", "Successfully imported content URI to $targetFile")
                targetFile
            } else {
                throw IllegalStateException("Failed to finalize imported file.")
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
    }

    /**
     * Resolves the display name and size of a content URI.
     */
    private fun resolveNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "External_Video_" + (System.currentTimeMillis() / 1000) + ".mp4"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val resolvedName = cursor.getString(nameIdx)
                        if (!resolvedName.isNullOrBlank()) {
                            name = resolvedName
                        }
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ContentUriImportManager", "Could not resolve metadata for content URI: ${e.message}")
        }
        return Pair(name, size)
    }

    /**
     * Sanitizes the filename by removing illegal chars.
     */
    private fun sanitizeFilename(name: String): String {
        return name.replace("[\\\\/:*?\"<>|\\s+]".toRegex(), "_")
    }

    /**
     * Deletes all files in the cache folder except the active file.
     */
    fun cleanupCacheExcept(activeFile: File?) {
        try {
            val files = cacheDir.listFiles() ?: return
            for (file in files) {
                if (file.exists() && (activeFile == null || file.absolutePath != activeFile.absolutePath)) {
                    val deleted = file.delete()
                    Log.d("ContentUriImportManager", "Cleaned up old cached file: ${file.name}, success: $deleted")
                }
            }
        } catch (e: Exception) {
            Log.e("ContentUriImportManager", "Error during cache cleanup", e)
        }
    }
}
