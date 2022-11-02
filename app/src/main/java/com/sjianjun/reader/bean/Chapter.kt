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
    var id: String = ""

    @JvmField
    var bookId = ""

    @JvmField
    var url: String = ""

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
        return "Chapter(url='$url', title=$title, isLoaded=$isLoaded, index=$index)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chapter

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }


}