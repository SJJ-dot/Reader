package io.legado.app.help.source

import io.legado.app.constant.SourceType
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

object SourceHelp {

    // 内存弱引用缓存：key -> WeakReference<BookSource>
    private val sourceCache: MutableMap<String, WeakReference<BookSource>> = ConcurrentHashMap()

    fun getSource(key: String?): BaseSource? {
        key ?: return null
        return sourceCache[key]?.get()
    }

    fun getSource(key: String?, @SourceType.Type type: Int): BaseSource? {
        key ?: return null
        return sourceCache[key]?.get()
    }

    /**
     * 将一个 BookSource 注册到内存弱引用缓存中，避免频繁从数据库读取。
     * 如果传入的 source 为 null 则忽略。
     */
    fun registerBookSource(source: BookSource?) {
        source ?: return
        sourceCache[source.bookSourceUrl] = WeakReference(source)
    }

}