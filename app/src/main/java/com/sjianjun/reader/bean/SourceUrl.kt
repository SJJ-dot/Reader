package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 订阅链接（书源订阅地址）
 *
 * 直接持久化到 Room 数据库，跟随数据库一起导出、导入、恢复。
 */
@Entity
data class SourceUrl(
    @PrimaryKey
    val url: String,
    val group: String,
    var autoUpdate: Boolean = false,
    /**
     * 列表展示顺序，数值越小越靠前
     */
    val sortOrder: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SourceUrl

        return url == other.url
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }

    override fun toString(): String {
        return "SourceUrl(url='$url', group=$group)"
    }

}
