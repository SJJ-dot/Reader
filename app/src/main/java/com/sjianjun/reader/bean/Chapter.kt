package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = arrayOf(Index(value = ["bookId"])))
class Chapter {
    @PrimaryKey(autoGenerate = true)
    @JvmField
    var id: Int = 0
    /**
     * 对应数据库id
     */
    @JvmField
    var bookId = 0
    @JvmField
    var title: String? = null
    @JvmField
    var url: String? = null

    @JvmField
    var isLoaded:Boolean = false

    /**
     * 章节内容
     */
    @JvmField
    var content: String? = null

    override fun toString(): String {
        return "Chapter(id=$id, bookId=$bookId, title='$title', url='$url', content='$content')"
    }


}