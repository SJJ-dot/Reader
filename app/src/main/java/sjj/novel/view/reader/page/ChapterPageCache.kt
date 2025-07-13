package sjj.novel.view.reader.page

import android.util.LruCache

object ChapterPageCache {
    private var bookId = ""

    //保留5个章节的分页缓存
    private val cache = LruCache<Int, List<TxtPage>>(5)
    fun reset(bookId: String) {
        if (bookId != this.bookId) {
            this.bookId = bookId
            cache.evictAll()
        }
    }

    fun reset() {
        cache.evictAll()
    }

    fun put(chapterPos: Int, pages: List<TxtPage>) {
        cache.put(chapterPos, pages)
    }

    fun get(chapterPos: Int): List<TxtPage>? {
        return cache.get(chapterPos)
    }
}