package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose

//以书籍名、作者、来源 3者确定书籍的唯一性
@Entity
class Book {
    @PrimaryKey
    var id: String = ""
        get() {
            if (field.isEmpty()) {
                field = url.trim()
            }
            return field
        }

    @JvmField
    var url: String = ""

    /**
     * 书籍来源[BookSource.source]
     */
    @Ignore
    @Expose(serialize = false)
    var source: String = ""
    @JvmField
    var bookSourceId: String = ""

    @JvmField
    var title: String = ""

    @JvmField
    var author: String = ""

    @JvmField
    var intro: String? = null

    @JvmField
    var cover: String? = null

    @JvmField
    @Expose(serialize = false)
    var isLoading = false

    @Ignore
    @JvmField
    @Expose(serialize = false)
    var chapterList: List<Chapter>? = null

    @Ignore
    @Expose(serialize = false)
    var lastChapter: Chapter? = null

    @Ignore
    @Expose(serialize = false)
    var readChapter: Chapter? = null

    @Ignore
    @Expose(serialize = false)
    var record: ReadingRecord? = null

    /**
     * 包含本书的书源
     */
    @Ignore
    @Expose(serialize = false)
    var javaScriptList: List<BookSource>? = null

    /**
     * 未读章节数量
     */
    @Ignore
    @Expose(serialize = false)
    var unreadChapterCount = 0

    /**
     * 书籍加载错误提示
     */
    @Expose(serialize = false)
    var error: String? = null

    @Ignore
    @Expose(serialize = false)
    var bookSource: BookSource? = null

    constructor()

    override fun toString(): String {
        return "Book(url=$url, bookSourceId=$bookSourceId, title=$title, author=$author, intro=$intro, cover=$cover, chapterList=$chapterList)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Book

        if (id != other.id) return false
        if (url != other.url) return false
        if (bookSourceId != other.bookSourceId) return false
        if (title != other.title) return false
        if (author != other.author) return false
        if (intro != other.intro) return false
        if (cover != other.cover) return false
        if (isLoading != other.isLoading) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + bookSourceId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + (intro?.hashCode() ?: 0)
        result = 31 * result + (cover?.hashCode() ?: 0)
        result = 31 * result + isLoading.hashCode()
        return result
    }


}