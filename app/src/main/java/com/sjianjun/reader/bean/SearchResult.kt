package com.sjianjun.reader.bean

/**
 * 搜索结果
 */
class SearchResult {
    @JvmField
    var source: String = ""
    @JvmField
    var bookTitle: String = ""
    @JvmField
    var bookAuthor: String = ""
    @JvmField
    var bookCover: String? = null
    @JvmField
    var bookUrl: String = ""

    /**
     * 最新章节 名
     */
    @JvmField
    var latestChapter: String? = null

    override fun toString(): String {
        return "SearchResult(bookTitle='$bookTitle', bookUrl='$bookUrl', bookAuthor='$bookAuthor', bookCover='$bookCover', latestChapter=$latestChapter)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchResult

        if (source != other.source) return false
        if (bookTitle != other.bookTitle) return false
        if (bookAuthor != other.bookAuthor) return false
        if (bookCover != other.bookCover) return false
        if (bookUrl != other.bookUrl) return false
        if (latestChapter != other.latestChapter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + bookTitle.hashCode()
        result = 31 * result + bookAuthor.hashCode()
        result = 31 * result + (bookCover?.hashCode() ?: 0)
        result = 31 * result + bookUrl.hashCode()
        result = 31 * result + (latestChapter?.hashCode() ?: 0)
        return result
    }

}