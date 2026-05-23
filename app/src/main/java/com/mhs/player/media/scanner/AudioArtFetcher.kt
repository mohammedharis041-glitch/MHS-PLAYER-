package com.mhs.player.media.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import java.io.ByteArrayInputStream

/**
 * Custom Coil Fetcher that extracts embedded album art from audio files.
 * This is used as a fallback when the MediaStore album art URI is null or missing.
 */
class AudioArtFetcher(
    private val context: Context,
    private val key: AudioArtKey
) : Fetcher {

    data class AudioArtKey(val uri: Uri)

    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, key.uri)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                if (bitmap != null) {
                    DrawableResult(
                        drawable = bitmap.toDrawable(context.resources),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                } else null
            } else null
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<AudioArtKey> {
        override fun create(data: AudioArtKey, options: Options, imageLoader: ImageLoader): Fetcher {
            return AudioArtFetcher(context, data)
        }
    }
}

// Extension to convert Bitmap to Drawable
private fun Bitmap.toDrawable(resources: android.content.res.Resources): android.graphics.drawable.BitmapDrawable {
    return android.graphics.drawable.BitmapDrawable(resources, this)
}
