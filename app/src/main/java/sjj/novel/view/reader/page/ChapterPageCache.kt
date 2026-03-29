package sjj.novel.view.reader.page

import android.util.LruCache
import sjj.alog.Log

object ChapterPageCache {
    private var id = ""
    private var textSize = 0f
    private var lineSpace = 0f
    private var displayWidth = 0
    private var displayHeight = 0

    //保留5个章节的分页缓存
    private val cache = LruCache<Int, List<TxtPage>>(5)
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

    fun resetTextSize(textSize: Float, lineSpace: Float) {
        if (this.textSize != textSize || this.lineSpace != lineSpace) {
            Log.d("resetTextSize: $textSize, lineSpace: $lineSpace")
            this.textSize = textSize
            this.lineSpace = lineSpace
            cache.evictAll()
        }
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