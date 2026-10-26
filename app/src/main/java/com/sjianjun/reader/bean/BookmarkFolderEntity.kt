package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏夹文件夹（支持嵌套）。
 * [id] 为真实 ID，显示名称 [name] 可随意修改而不影响引用。
 */
@Entity(tableName = "bookmark_folder")
data class BookmarkFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String = "root",
    val builtin: Boolean = false,
    val time: Long = System.currentTimeMillis()
)
