package com.mhs.player.player.controller

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeekThumbnailProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val thumbnailCache = LruCache<String, Bitmap>(50) // Cache 50 thumbnails
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentRetriever: MediaMetadataRetriever? = null
    private var currentPath: String? = null

    /**
     * Get a thumbnail at the specified time (in microseconds).
     * Returns a Bitmap if found or extracted, null otherwise.
     */
    suspend fun getThumbnail(path: String, timeUs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${path}_${timeUs / 1_000_000}" // Cache per second for performance
        
        thumbnailCache.get(cacheKey)?.let { return@withContext it }

        try {
            if (currentPath != path) {
                currentRetriever?.release()
                currentRetriever = MediaMetadataRetriever().apply {
                    setDataSource(path)
                }
                currentPath = path
            }

            // OPTION_CLOSEST_SYNC is faster, OPTION_CLOSEST is more accurate but slower
            val bitmap = currentRetriever?.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

            if (bitmap != null) {
                // Scale down for preview
                val scaled = Bitmap.createScaledBitmap(bitmap, 240, 135, true)
                thumbnailCache.put(cacheKey, scaled)
                return@withContext scaled
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    fun release() {
        scope.launch {
            currentRetriever?.release()
            currentRetriever = null
            currentPath = null
            thumbnailCache.evictAll()
        }
    }
}
