package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Chapter {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
    var bookId = 0

    var title = ""
    var url = ""
    override fun toString(): String {
        return "Chapter(id=$id, bookId=$bookId, title='$title', url='$url')"
    }

}