package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
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
    var title = ""
    @JvmField
    var url = ""

    /**
     * 章节内容
     */
    var content = ""

    override fun toString(): String {
        return "Chapter(id=$id, bookId=$bookId, title='$title', url='$url', content='$content')"
    }


}