package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import java.util.UUID

//以书籍名、作者、来源 3者确定书籍的唯一性
@Entity(indices = [Index(value = ["author", "title", "source"])])
class Book {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()

    @JvmField
    var url: String = ""

    /**
     * 书籍来源[BookSource.source]
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

    override fun toString(): String {
        return "Book(url=$url, source=$source, title=$title, author=$author, intro=$intro, cover=$cover, chapterList=$chapterList)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Book

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }


}