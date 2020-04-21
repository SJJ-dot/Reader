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

    var offest = 0

    var isEnd = false

    /**
     * 首发站书籍地址
     */
    var startingStationBookSource = ""

    override fun toString(): String {
        return "ReadingRecord(bookTitle='$bookTitle', bookAuthor='$bookAuthor', bookUrl='$bookUrl', chapterUrl='$chapterUrl')"
    }

}