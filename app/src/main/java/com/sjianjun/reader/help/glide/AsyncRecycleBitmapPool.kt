package com.sjianjun.reader.help.glide

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPoolAdapter
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val recycleExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

// 与 io.legado.app.help.glide.AsyncRecycleBitmapPool 行为一致：put 时异步回收 Bitmap
class AsyncRecycleBitmapPool(private val delegate: BitmapPool) : BitmapPool by delegate {

    constructor(maxSize: Int) : this(
        if (maxSize > 0) {
            LruBitmapPool(maxSize.toLong())
        } else {
            BitmapPoolAdapter()
        }
    )

    override fun put(bitmap: Bitmap) {
        recycleExecutor.execute {
            delegate.put(bitmap)
        }
    }
}
