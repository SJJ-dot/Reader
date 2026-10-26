package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏项。[folderId] 指向 [BookmarkFolderEntity.id]（"root" 表示根目录）。
 * [orderIndex] 维护同一文件夹内的排序（拖动排序时使用）。
 */
@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val folderId: String = "root",
    val orderIndex: Int = 0,
    val time: Long = System.currentTimeMillis()
)
