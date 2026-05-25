package com.sjianjun.reader

import android.content.Context
import android.util.Log
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.module.AppGlideModule
import io.legado.app.help.glide.AsyncRecycleBitmapPool

@GlideModule
class MyAppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // configure memory/disk cache and bitmap pool centrally in the AppGlideModule
        val calculator = MemorySizeCalculator.Builder(context).build()
        val bitmapPool = AsyncRecycleBitmapPool(calculator.bitmapPoolSize)
        builder.setMemorySizeCalculator(calculator)
        builder.setBitmapPool(bitmapPool)
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, 1024 * 1024 * 100))
        if (!BuildConfig.DEBUG) {
            builder.setLogLevel(Log.ERROR)
        }
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}