package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * 搜索结果
 */
@Entity
class SearchResult {
    @PrimaryKey(autoGenerate = true)
    @JvmField
    var id: Int = 0
    @JvmField
    var source: String = ""
    @JvmField
    var bookTitle = ""
    @JvmField
    var bookUrl: String = ""
    @JvmField
    var bookAuthor = ""
    @JvmField
    var bookCover: String = ""
    @Ignore
    @JvmField
    var lastChapter: Chapter? = null

    override fun toString(): String {
        return "SearchResult(id=$id, bookTitle='$bookTitle', bookUrl='$bookUrl', bookAuthor='$bookAuthor', bookCover='$bookCover', lastChapter=$lastChapter)"
    }


}