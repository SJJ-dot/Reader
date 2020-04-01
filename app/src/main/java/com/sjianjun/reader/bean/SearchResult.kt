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
    var id: Int = 0
    var bookTitle = ""
    var bookUrl: String = ""
    var bookAuthor = ""
    var bookCover: String = ""

    @Ignore
    var lastChapter: Chapter? = null

    override fun toString(): String {
        return "SearchResult(id=$id, bookTitle='$bookTitle', bookUrl='$bookUrl', bookAuthor='$bookAuthor', bookCover='$bookCover', lastChapter=$lastChapter)"
    }


}