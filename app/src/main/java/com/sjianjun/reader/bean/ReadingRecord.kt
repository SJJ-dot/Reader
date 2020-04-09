package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(primaryKeys = ["bookTitle", "bookAuthor"])
class ReadingRecord(
    var bookTitle: String = "",
    var bookAuthor: String = ""
) {

    var bookUrl = ""

    var chapterUrl = ""
}