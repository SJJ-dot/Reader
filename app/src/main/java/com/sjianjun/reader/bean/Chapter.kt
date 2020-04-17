package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["bookUrl"])])
class Chapter {
    @JvmField
    @PrimaryKey
    var url: String = ""
    /**
     * 对应数据库id
     */
    @JvmField
    var bookUrl = ""
    @JvmField
    var title: String? = null

    @JvmField
    var isLoaded:Boolean = false

    /**
     * 章节内容
     */
    @JvmField
    @Ignore
    var content: ChapterContent? = null

    /**
     * 章节索引
     */
    @JvmField
    var index = 0

    @JvmField
    @Ignore
    var isLastChapter = true

    override fun toString(): String {
        return "Chapter(url='$url', bookUrl='$bookUrl', title=$title, isLoaded=$isLoaded, index=$index)"
    }

}