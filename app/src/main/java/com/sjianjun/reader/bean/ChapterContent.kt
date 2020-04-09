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
)