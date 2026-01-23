package com.skeler.scanely

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp

/**
 * Scanly Application class with optimized Coil ImageLoader configuration.
 * 
 * Performance optimizations:
 * - Memory cache: 25% of available heap (balanced for scanning apps)
 * - Disk cache: 100MB for product images
 * - Cross-fade disabled for faster perceived loading
 * - Aggressive caching policies
 */
@HiltAndroidApp
class App : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of available heap
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100MB
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false) // Disable for faster perceived loading
            .respectCacheHeaders(false) // Cache even if server says no-cache
            .build()
    }
}