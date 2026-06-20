package com.tama.gallerynoai

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import android.os.Build
import com.tama.gallerynoai.data.settings.SettingsManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class GalleryApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun newImageLoader(): ImageLoader {
        // Use runBlocking as newImageLoader is called by Coil when needed, 
        // and we need the value synchronously here for initialization.
        val diskCacheMb = runBlocking {
            settingsManager.diskCacheMb.first()
        }

        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Use 25% of available memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskCacheMb.toLong() * 1024 * 1024)
                    .build()
            }
            .crossfade(100) // Shorter crossfade for snappier feel
            .respectCacheHeaders(false) // Local files don't change often
            .build()
    }
}
