package com.sjianjun.reader.bean

import androidx.room.Entity
import com.sjianjun.reader.utils.format

/**
 * 章节内容。
 *
 * 内容以章节链接(chapterUrl)作为唯一标识：目录更新时章节索引(index)会被重新分配
 * （补齐中间缺失的章节、中间插入新章节都会导致索引位移），如果内容按索引存储，
 * 索引更新后已缓存的内容就会和章节错位。改用 chapterUrl 后，内容始终跟随章节本身。
 */
@Entity(primaryKeys = ["bookId", "chapterUrl", "pageIndex"])
class ChapterContent {
    var bookId: String = ""
    //章节链接，章节的唯一标识
    var chapterUrl: String = ""
    var contentError: Boolean = false
    //当前分页索引，默认为0。由客户端维护
    var pageIndex: Int = 0
    //章节内容
    var content: String? = null
    //下一页地址,没有分页为null，由脚本维护
    var nextPageUrl: String? = null

    fun format(): CharSequence {
        return content.format()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChapterContent

        if (chapterUrl != other.chapterUrl) return false
        if (bookId != other.bookId) return false
        if (content != other.content) return false
        if (contentError != other.contentError) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chapterUrl.hashCode()
        result = 31 * result + bookId.hashCode()
        result = 31 * result + (content?.hashCode() ?: 0)
        result = 31 * result + contentError.hashCode()
        return result
    }

    override fun toString(): String {
        return "ChapterContent(content=$content, pageIndex=$pageIndex, nextPageUrl=$nextPageUrl, chapterUrl='$chapterUrl', bookId='$bookId', contentError=$contentError)"
    }


    companion object {
        operator fun invoke(
            bookId: String,
            chapterUrl: String,
            chapterContent: String,
            contentError: Boolean = false
        ): ChapterContent {
            val cc = ChapterContent()
            cc.bookId = bookId
            cc.chapterUrl = chapterUrl
            cc.content = chapterContent
            cc.contentError = contentError
            return cc
        }
    }
}
