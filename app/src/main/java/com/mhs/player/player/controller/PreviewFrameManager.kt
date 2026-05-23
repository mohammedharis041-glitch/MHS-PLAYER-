package com.mhs.player.player.controller

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.collection.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreviewFrameManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fastRetriever: MediaMetadataRetriever? = null
    
    @Volatile
    var isHeavyVideoMode: Boolean = false
        set(value) {
            field = value
            Log.d("MHSPlayer-Preview", "isHeavyVideoMode updated to $value — previews remain active for all formats.")
        }
    private var exactRetriever: MediaMetadataRetriever? = null
    private var currentUri: Uri? = null

    private val cache = LruCache<Long, Bitmap>(100) // Increased cache for better responsiveness

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private var activeJob: Job? = null
    private var pendingPositionMs: Long? = null
    private var inactivityJob: Job? = null

    fun prepareRetriever(uri: Uri) {
        if (uri == currentUri) return
        currentUri = uri
        cache.evictAll()
        Log.d("MHSPlayer-Preview", "prepareRetriever set uri: $uri, deferring allocations to lazy on-demand load")
        
        // Cancel any pending inactivity releases since we changed track
        inactivityJob?.cancel()
        
        // Also release existing retrievers for previous track immediately
        fastRetriever?.release()
        exactRetriever?.release()
        fastRetriever = null
        exactRetriever = null
    }

    private suspend fun ensureRetrieversLoaded() {
        val uri = currentUri ?: return
        if (fastRetriever != null && exactRetriever != null) return

        Log.d("MHSPlayer-Preview", "Lazy-loading preview retrievers on-demand for $uri")
        withContext(Dispatchers.IO) {
            val newFast = MediaMetadataRetriever()
            val newExact = MediaMetadataRetriever()
            try {
                val loadFast = async {
                    try {
                        if (uri.scheme == "content") {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                newFast.setDataSource(pfd.fileDescriptor)
                            }
                        } else {
                            val path = uri.path
                            if (path != null && java.io.File(path).exists()) {
                                newFast.setDataSource(path)
                            } else {
                                newFast.setDataSource(context, uri)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        Log.e("MHSPlayer-Preview", "FAILED to lazy-load fastRetriever for $uri", e)
                        false
                    }
                }

                val loadExact = async {
                    try {
                        if (uri.scheme == "content") {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                newExact.setDataSource(pfd.fileDescriptor)
                            }
                        } else {
                            val path = uri.path
                            if (path != null && java.io.File(path).exists()) {
                                newExact.setDataSource(path)
                            } else {
                                newExact.setDataSource(context, uri)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        Log.e("MHSPlayer-Preview", "FAILED to lazy-load exactRetriever for $uri", e)
                        false
                    }
                }

                if (loadFast.await() && loadExact.await()) {
                    fastRetriever?.release()
                    exactRetriever?.release()
                    fastRetriever = newFast
                    exactRetriever = newExact
                    Log.d("MHSPlayer-Preview", "Successfully lazy-loaded both retrievers on-demand")
                } else {
                    newFast.release()
                    newExact.release()
                }
            } catch (e: Exception) {
                Log.e("MHSPlayer-Preview", "Error in lazy-loading retrievers", e)
                newFast.release()
                newExact.release()
            }
        }
    }

    fun requestPreview(positionMs: Long, highAccuracy: Boolean = false) {
        _isVisible.value = true
        inactivityJob?.cancel() // Cancel inactivity release if scrubbing resumed!

        // Synchronous cache hit bypass for instant 120fps scrubbing responsiveness
        val cacheKey = if (highAccuracy) -positionMs else positionMs / 500
        val cached = cache.get(cacheKey)
        if (cached != null) {
            _previewBitmap.value = cached
            return
        }

        if (highAccuracy) {
            // Cancel low accuracy jobs for priority precision
            activeJob?.cancel()
            activeJob = scope.launch {
                ensureRetrieversLoaded()
                extractFrame(positionMs, highAccuracy = true)
            }
            return
        }

        // For real-time scrolling, if we are already extracting a frame,
        // just queue the latest position and let the current frame complete decoding.
        if (activeJob?.isActive == true) {
            pendingPositionMs = positionMs
            return
        }

        pendingPositionMs = null
        activeJob = scope.launch {
            ensureRetrieversLoaded()
            extractFrame(positionMs, highAccuracy = false)
            processPendingRequests()
        }
    }

    private suspend fun processPendingRequests() {
        val nextPos = pendingPositionMs
        if (nextPos != null) {
            pendingPositionMs = null
            activeJob = scope.launch {
                ensureRetrieversLoaded()
                extractFrame(nextPos, highAccuracy = false)
                processPendingRequests()
            }
        }
    }

    private suspend fun extractFrame(positionMs: Long, highAccuracy: Boolean) {
        val currentRetriever = (if (highAccuracy) exactRetriever else fastRetriever) ?: fastRetriever ?: exactRetriever ?: return
        val cacheKey = if (highAccuracy) -positionMs else positionMs / 500
        
        try {
            val bmp = withContext(Dispatchers.Default) {
                val timeUs = positionMs * 1000
                val targetW = 240
                val targetH = 135
                var resultBmp: Bitmap? = null

                // Try hardware-accelerated scaled frame extraction (API 27+)
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                        resultBmp = currentRetriever.getScaledFrameAtTime(
                            timeUs,
                            if (highAccuracy) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            targetW,
                            targetH
                        )
                    }
                } catch (e: Exception) {
                    Log.w("MHSPlayer-Preview", "getScaledFrameAtTime failed, trying fallback", e)
                }

                // Fallback 1: getFrameAtTime + manual scale down
                if (resultBmp == null) {
                    try {
                        val fullBmp = currentRetriever.getFrameAtTime(
                            timeUs,
                            if (highAccuracy) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        fullBmp?.let {
                            resultBmp = Bitmap.createScaledBitmap(it, targetW, targetH, true)
                            if (resultBmp != it) it.recycle()
                        }
                    } catch (e: Exception) {
                        Log.w("MHSPlayer-Preview", "Manual scaled extraction failed, trying sync fallback", e)
                    }
                }

                // Fallback 2: Standard sync options (highly reliable)
                if (resultBmp == null) {
                    try {
                        val fullBmp = currentRetriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        fullBmp?.let {
                            resultBmp = Bitmap.createScaledBitmap(it, targetW, targetH, true)
                            if (resultBmp != it) it.recycle()
                        }
                    } catch (e: Exception) {
                        Log.e("MHSPlayer-Preview", "All frame extraction attempts failed for $positionMs", e)
                    }
                }

                resultBmp
            }
            
            if (bmp != null) {
                cache.put(cacheKey, bmp)
                _previewBitmap.value = bmp
            }
        } catch (e: Exception) {
            Log.e("MHSPlayer-Preview", "Error in extractFrame pipeline", e)
        }
    }

    fun hidePreview() {
        _isVisible.value = false
        _previewBitmap.value = null
        activeJob?.cancel()
        pendingPositionMs = null

        // Start 5-second inactivity timer to aggressively release retrievers
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            delay(5000)
            releaseRetrieversOnly()
        }
    }

    private fun releaseRetrieversOnly() {
        Log.d("MHSPlayer-Preview", "Inactivity timer fired. Aggressively releasing preview retrievers to free up system hardware decoders.")
        fastRetriever?.release()
        exactRetriever?.release()
        fastRetriever = null
        exactRetriever = null
    }

    fun release() {
        inactivityJob?.cancel()
        inactivityJob = null
        activeJob?.cancel()
        pendingPositionMs = null
        fastRetriever?.release()
        exactRetriever?.release()
        fastRetriever = null
        exactRetriever = null
        currentUri = null
        cache.evictAll()
    }
}
