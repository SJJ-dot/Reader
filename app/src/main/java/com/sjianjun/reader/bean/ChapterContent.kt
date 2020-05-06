package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ChapterContent(
    /**
     * 章节内容主键应该和章节信息主键相同
     */
    @PrimaryKey
    var url: String = "",

    var bookUrl: String = "",

    var content: String? = null

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChapterContent

        if (url != other.url) return false
        if (bookUrl != other.bookUrl) return false
        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + bookUrl.hashCode()
        result = 31 * result + (content?.hashCode() ?: 0)
        return result
    }
}