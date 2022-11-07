package com.sjianjun.test.bean


class ChapterContent {
    var chapterIndex: Int = 0
    var bookId: String = ""
    var content: String? = null


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChapterContent

        if (chapterIndex != other.chapterIndex) return false
        if (bookId != other.bookId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chapterIndex
        result = 31 * result + bookId.hashCode()
        return result
    }


    companion object {

        operator fun invoke(bookId: String, chapterIndex: Int, chapterContent: String): ChapterContent {
            val cc = ChapterContent()
            cc.bookId = bookId
            cc.chapterIndex = chapterIndex
            cc.content = chapterContent
            return cc
        }
    }
}