package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

//以书籍名、作者、来源 3者确定书籍的唯一性
@Entity(indices = [Index(value = ["author","title","source"],unique = true)])
class Book {
    @PrimaryKey(autoGenerate = true)
    var id = 0
    /**
     * 书籍来源[JavaScript.source]
     */
    @JvmField
    var source: String? = null
    @JvmField
    var title: String? = null
    @JvmField
    var author: String? = null
    @JvmField
    var intro: String? = null
    @JvmField
    var cover: String? = null
    @JvmField
    var url: String? = null
    @Ignore
    @JvmField
    var chapterList: List<Chapter>? = null

    override fun toString(): String {
        return "Book(id=$id, source=$source, title=$title, author=$author, intro=$intro, cover=$cover, url=$url, chapterList=$chapterList)"
    }


}