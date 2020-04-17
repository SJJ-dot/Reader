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

}