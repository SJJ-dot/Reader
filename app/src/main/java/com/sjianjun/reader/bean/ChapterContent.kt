package com.sjianjun.reader.bean

import android.text.SpannableStringBuilder
import androidx.collection.LruCache
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sjianjun.reader.utils.html

@Entity
data class ChapterContent(
    /**
     * 章节内容主键应该和章节信息主键相同
     */
    @PrimaryKey
    var url: String,

    var bookUrl: String,

    var content: String?

) {

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

        if (url != other.url) return false
        if (bookUrl != other.bookUrl) return false
        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + bookUrl.hashCode()
        result = 31 * result + (content?.hashCode() ?: 0)
        return result
    }

    companion object {
        val cache = LruCache<String, SpannableStringBuilder>(5)
    }
}