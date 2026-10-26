package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 浏览器历史记录（按 [time] 倒序展示）。 */
@Entity(tableName = "browser_history")
data class BrowserHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val time: Long
)
