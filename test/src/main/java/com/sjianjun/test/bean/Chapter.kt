package com.sjianjun.test.bean

import java.util.concurrent.atomic.AtomicBoolean

class Chapter {

    @JvmField
    var url: String = ""

    @JvmField
    var bookId = ""

    @JvmField
    var title: String? = null

    @JvmField
    var isLoaded: Boolean = false

    var isLoading = AtomicBoolean(false)

    /**
     * 章节内容
     */
    @JvmField
    var content: ChapterContent? = null

    /**
     * 章节索引
     */
    @JvmField
    var index = 0

    override fun toString(): String {
        return "Chapter(url='$url', title=$title, isLoaded=$isLoaded, index=$index)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chapter

        if (bookId != other.bookId) return false
        if (index != other.index) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bookId.hashCode()
        result = 31 * result + index
        return result
    }
}