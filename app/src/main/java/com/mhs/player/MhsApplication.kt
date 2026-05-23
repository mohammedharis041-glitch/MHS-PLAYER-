package com.mhs.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mhs.player.media.scanner.AudioArtFetcher
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MhsApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
    }
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                // Enables loading embedded album art directly from audio files
                // via MediaMetadataRetriever when the MediaStore album art URI fails.
                add(AudioArtFetcher.Factory(this@MhsApplication))
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizePercent(0.04)   // 4% — album art needs a bit more room
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .allowHardware(false)  // Required for ContentResolver album art URIs
            .build()
    }
}
