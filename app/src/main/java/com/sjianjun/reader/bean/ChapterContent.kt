package com.sjianjun.reader.bean

import android.text.SpannableStringBuilder
import androidx.room.Entity
import com.sjianjun.reader.utils.html

@Entity(primaryKeys = ["chapterIndex", "bookId", "pageIndex"])
class ChapterContent {
    var bookId: String = ""
    var chapterIndex: Int = 0
    var contentError: Boolean = false
    //当前分页索引，默认为0。由客户端维护
    var pageIndex: Int = 0
    //章节内容
    var content: String? = null
    //下一页地址,没有分页为null，由脚本维护
    var nextPageUrl: String? = null

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
        return locContent
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChapterContent

        if (chapterIndex != other.chapterIndex) return false
        if (bookId != other.bookId) return false
        if (content != other.content) return false
        if (contentError != other.contentError) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chapterIndex
        result = 31 * result + bookId.hashCode()
        result = 31 * result + (content?.hashCode() ?: 0)
        result = 31 * result + contentError.hashCode()
        return result
    }


    companion object {
        operator fun invoke(
            bookId: String,
            chapterIndex: Int,
            chapterContent: String,
            contentError: Boolean = false
        ): ChapterContent {
            val cc = ChapterContent()
            cc.bookId = bookId
            cc.chapterIndex = chapterIndex
            cc.content = chapterContent
            cc.contentError = contentError
            return cc
        }
    }
}