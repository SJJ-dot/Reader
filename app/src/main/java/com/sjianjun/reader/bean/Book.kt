package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

//以书籍名、作者、来源 3者确定书籍的唯一性
@Entity(indices = [Index(value = ["author", "title", "source"], unique = true)])
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

    override fun toString(): String {
        return "Book(url=$url, source=$source, title=$title, author=$author, intro=$intro, cover=$cover, chapterList=$chapterList)"
    }

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
}