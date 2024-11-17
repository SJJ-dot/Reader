package com.sjianjun.reader.bean

import androidx.room.Entity

@Entity(primaryKeys = ["bookTitle"])
class ReadingRecord(
    var bookTitle: String,
    var bookId: String = ""
) {
    var chapterIndex = 0

    /**
     * 原来没有页数用这个值当作页数
     */
    var offest = 0

    var isEnd = false



    override fun toString(): String {
        return "ReadingRecord(bookTitle='$bookTitle', chapterIndex=$chapterIndex, isEnd=$isEnd)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReadingRecord

        if (bookTitle != other.bookTitle) return false
        if (bookId != other.bookId) return false
        if (chapterIndex != other.chapterIndex) return false
        if (offest != other.offest) return false
        if (isEnd != other.isEnd) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bookTitle.hashCode()
        result = 31 * result + bookId.hashCode()
        result = 31 * result + chapterIndex
        result = 31 * result + offest
        result = 31 * result + isEnd.hashCode()
        return result
    }

}