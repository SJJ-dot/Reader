package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

//以书籍名、作者、来源 3者确定书籍的唯一性
@Entity(indices = [Index(value = ["author", "title", "source"])])
class Book {
    @JvmField
    @PrimaryKey
    var url: String = ""

    /**
     * 书籍来源[JavaScript.source]
     */
    @JvmField
    var source: String = ""

    @JvmField
    var title: String = ""

    @JvmField
    var author: String = ""

    @JvmField
    var intro: String? = null

    @JvmField
    var cover: String? = null

    @JvmField
    var isLoading = false

    @Ignore
    @JvmField
    var chapterList: List<Chapter>? = null

    @Ignore
    var lastChapter: Chapter? = null

    @Ignore
    var readChapter: Chapter? = null

    @Ignore
    var record: ReadingRecord? = null

    /**
     * 包含本书的书源
     */
    @Ignore
    var javaScriptList: List<JavaScript>? = null

    /**
     * 未读章节数量
     */
    @Ignore
    var unreadChapterCount = 0

    /**
     * 书籍加载错误提示
     */
    var error: String? = null

    @Ignore
    var startingError: String? = null

    override fun toString(): String {
        return "Book(url=$url, source=$source, title=$title, author=$author, intro=$intro, cover=$cover, chapterList=$chapterList)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Book

        if (url != other.url) return false
        if (source != other.source) return false
        if (title != other.title) return false
        if (author != other.author) return false
        if (intro != other.intro) return false
        if (cover != other.cover) return false
        if (isLoading != other.isLoading) return false
        if (chapterList != other.chapterList) return false
        if (lastChapter != other.lastChapter) return false
        if (readChapter != other.readChapter) return false
        if (record != other.record) return false
        if (javaScriptList != other.javaScriptList) return false
        if (unreadChapterCount != other.unreadChapterCount) return false
        if (error != other.error) return false
        if (startingError != other.startingError) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + (intro?.hashCode() ?: 0)
        result = 31 * result + (cover?.hashCode() ?: 0)
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (chapterList?.hashCode() ?: 0)
        result = 31 * result + (lastChapter?.hashCode() ?: 0)
        result = 31 * result + (readChapter?.hashCode() ?: 0)
        result = 31 * result + (record?.hashCode() ?: 0)
        result = 31 * result + (javaScriptList?.hashCode() ?: 0)
        result = 31 * result + unreadChapterCount
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (startingError?.hashCode() ?: 0)
        return result
    }


}