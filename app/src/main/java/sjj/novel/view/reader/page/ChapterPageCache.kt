package sjj.novel.view.reader.page

import android.util.LruCache
import sjj.alog.Log

object ChapterPageCache {
    private var id = ""
    private var displayWidth = 0
    private var displayHeight = 0

    // 只保留必要的分页缓存：当前章 + 上一章 + 下一章（预加载），共 3 章
    private val cache = LruCache<Int, List<TxtPage>>(3)

    fun reset() {
        cache.evictAll()
    }

    fun resetId(id: String) {
        if (id != this.id) {
            Log.d("resetId: $id")
            this.id = id
            cache.evictAll()
        }
    }

    fun put(chapterPos: Int, pages: List<TxtPage>) {
        cache.put(chapterPos, pages)
    }

    fun get(chapterPos: Int): List<TxtPage>? {
        return cache.get(chapterPos)
    }

    @JvmStatic
    fun remove(chapterPos: Int) {
        cache.remove(chapterPos)
    }

    fun resetDisplay(w: Int, h: Int) {
        if (displayWidth != w || displayHeight != h) {
            Log.d("resetDisplay: width: $w, height: $h")
            displayWidth = w
            displayHeight = h
            cache.evictAll()
        }
    }
}
