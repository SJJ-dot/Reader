package com.sjianjun.reader.bean

import androidx.room.Entity
import com.sjianjun.reader.utils.STARTING_STATION_BOOK_SOURCE_EMPTY

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
     * [STARTING_STATION_BOOK_SOURCE_EMPTY]
     */
    var startingStationBookSource = ""

    override fun toString(): String {
        return "ReadingRecord(bookTitle='$bookTitle', bookAuthor='$bookAuthor', bookUrl='$bookUrl', chapterUrl='$chapterUrl')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReadingRecord

        if (bookTitle != other.bookTitle) return false
        if (bookAuthor != other.bookAuthor) return false
        if (bookUrl != other.bookUrl) return false
        if (chapterUrl != other.chapterUrl) return false
        if (offest != other.offest) return false
        if (isEnd != other.isEnd) return false
        if (startingStationBookSource != other.startingStationBookSource) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bookTitle.hashCode()
        result = 31 * result + bookAuthor.hashCode()
        result = 31 * result + bookUrl.hashCode()
        result = 31 * result + chapterUrl.hashCode()
        result = 31 * result + offest
        result = 31 * result + isEnd.hashCode()
        result = 31 * result + startingStationBookSource.hashCode()
        return result
    }


}