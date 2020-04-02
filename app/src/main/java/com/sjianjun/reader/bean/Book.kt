package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

//以书籍名、作者、来源 3者确定书籍的唯一性
@Entity
class Book {
    @PrimaryKey(autoGenerate = true)
    @JvmField
    var id: Int = 0
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
    var intro: String = ""
    @JvmField
    var cover: String = ""
    @Ignore
    @JvmField
    var chapterList: List<Chapter>? = null

    override fun toString(): String {
        return "Book(id=$id, source='$source', title='$title', author='$author', intro='$intro', cover='$cover', chapterList=$chapterList)"
    }


}