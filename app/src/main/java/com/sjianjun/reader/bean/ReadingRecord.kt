package com.sjianjun.reader.bean

import androidx.room.Entity

@Entity(primaryKeys = ["bookTitle", "bookAuthor"])
class ReadingRecord(
    var bookTitle: String,
    var bookAuthor: String,
    var bookId: String = ""
) {
    var chapterIndex = 0

    /**
     * 原来没有页数用这个值当作页数
     */
    var offest = 0

    var isEnd = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReadingRecord

        if (bookTitle != other.bookTitle) return false
        if (bookAuthor != other.bookAuthor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bookTitle.hashCode()
        result = 31 * result + bookAuthor.hashCode()
        return result
    }

    override fun toString(): String {
        return "ReadingRecord(bookTitle='$bookTitle', bookAuthor='$bookAuthor', chapterIndex=$chapterIndex, isEnd=$isEnd)"
    }

}