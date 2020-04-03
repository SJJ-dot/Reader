package com.sjianjun.reader.bean

import com.sjianjun.reader.utils.id

/**
 * 搜索结果
 */
class SearchResult {
    @JvmField
    var source: String = ""
    @JvmField
    var bookTitle: String? = null
    @JvmField
    var bookAuthor: String? = null
    @JvmField
    var bookCover: String? = null
    @JvmField
    var bookUrl: String? = null

    /**
     * 最新章节 名
     */
    @JvmField
    var latestChapter: String? = null

    val id: Long by lazy { bookUrl?.id ?: 0 }

    override fun toString(): String {
        return "SearchResult(id=$id, bookTitle='$bookTitle', bookUrl='$bookUrl', bookAuthor='$bookAuthor', bookCover='$bookCover', latestChapter=$latestChapter)"
    }


}