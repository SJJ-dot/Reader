package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 浏览器标签页（仅持久化元数据，WebView 在需要时重建）。
 * [historyJson] / [historyIndex] 复用原前进后退栈的序列化结构。
 */
@Entity(tableName = "browser_tab")
data class BrowserTabEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val orderIndex: Int,
    val isActive: Boolean,
    val thumbPath: String?,
    val historyJson: String,
    val historyIndex: Int,
    val lastUsedAt: Long
)
