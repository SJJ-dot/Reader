package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.concurrent.atomic.AtomicBoolean

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
    var isLoaded: Boolean = false

    @Ignore
    var isLoading = AtomicBoolean(false)

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chapter

        if (url != other.url) return false
        if (bookUrl != other.bookUrl) return false
        if (title != other.title) return false
        if (isLoaded != other.isLoaded) return false
        if (content != other.content) return false
        if (index != other.index) return false
        if (isLastChapter != other.isLastChapter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + bookUrl.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + isLoaded.hashCode()
        result = 31 * result + (content?.hashCode() ?: 0)
        result = 31 * result + index
        result = 31 * result + isLastChapter.hashCode()
        return result
    }


}