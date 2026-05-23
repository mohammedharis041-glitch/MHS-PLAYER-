package com.mhs.player.core.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView

object ScreenshotHelper {

    fun captureScreenshot(
        surfaceView: SurfaceView,
        context: Context,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (surfaceView.width <= 0 || surfaceView.height <= 0) {
            onResult(false, "Invalid video surface dimensions")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val bitmap = Bitmap.createBitmap(
                    surfaceView.width,
                    surfaceView.height,
                    Bitmap.Config.ARGB_8888
                )
                
                val handlerThread = HandlerThread("PixelCopyThread")
                handlerThread.start()
                val handler = Handler(handlerThread.looper)

                PixelCopy.request(surfaceView, bitmap, { copyResult ->
                    handlerThread.quitSafely()
                    if (copyResult == PixelCopy.SUCCESS) {
                        saveBitmapToGallery(bitmap, context, onResult)
                    } else {
                        Log.e("MHSPlayer-Screenshot", "PixelCopy failed: code $copyResult")
                        onResult(false, "Failed to capture video frame (Code: $copyResult)")
                    }
                }, handler)
            } catch (e: Exception) {
                Log.e("MHSPlayer-Screenshot", "Error during PixelCopy setup", e)
                onResult(false, "Screenshot error: ${e.localizedMessage}")
            }
        } else {
            onResult(false, "Device API level too low for high-fidelity capture")
        }
    }

    private fun saveBitmapToGallery(
        bitmap: Bitmap,
        context: Context,
        onResult: (Boolean, String?) -> Unit
    ) {
        val filename = "MHSPlayer_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MHSPlayer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                onResult(true, "Saved to Pictures/MHSPlayer")
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                Log.e("MHSPlayer-Screenshot", "Error saving bitmap to stream", e)
                onResult(false, "Failed to save: ${e.localizedMessage}")
            }
        } else {
            onResult(false, "Failed to allocate MediaStore entry")
        }
    }
}
