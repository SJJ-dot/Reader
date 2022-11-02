package com.sjianjun.reader.bean

import android.text.SpannableStringBuilder
import androidx.collection.LruCache
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sjianjun.reader.utils.html

@Entity
class ChapterContent {
    /**
     * 章节内容主键应该和章节信息主键相同
     */
    @PrimaryKey
    var id: String = ""
    var bookId: String = ""

    var content: String? = null

    fun cacheFormat(): SpannableStringBuilder? {
        return cache.get(content ?: return null)
    }

    fun format(): SpannableStringBuilder {
        val content = content ?: return SpannableStringBuilder()
        val locContent = SpannableStringBuilder(content.html())
        Regex("\\A\\s*").find(locContent)?.apply {
            locContent.replace(range.first, range.last + 1, "")
        }
        locContent.insert(0, "\u3000\u3000")
        var result = Regex("\n+\\s*").find(locContent)
        while (result != null) {
            locContent.replace(result.range.first, result.range.last + 1, "\n\u3000\u3000")
            result = result.next()
        }
        cache.put(content, locContent)
        return locContent
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChapterContent

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        val cache = LruCache<String, SpannableStringBuilder>(5)
    }
}