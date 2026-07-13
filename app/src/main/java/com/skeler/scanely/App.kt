package com.skeler.scanely

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.skeler.scanely.core.ocr.paddle.PaddleOcrEngine
import com.skeler.scanely.core.security.Secrets
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), ImageLoaderFactory {

    // Lazy so Application.onCreate doesn't build the OCR graph; only onTrimMemory needs it.
    @Inject lateinit var paddleOcrEngine: dagger.Lazy<PaddleOcrEngine>

    override fun onCreate() {
        super.onCreate()
        // Pin bundled keys to this build's signature before any provider resolves.
        Secrets.init(this)
    }

    /** OCR sessions hold ~30 MB of native memory; drop them when the system asks. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) paddleOcrEngine.get().close()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false) // faster perceived loading
            .respectCacheHeaders(false) // Cache even if server says no-cache
            .build()
    }
}
