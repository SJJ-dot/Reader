package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class WebBook {
    //id 使用第一次输入的url。
    @PrimaryKey
    var id: String = ""
        get() {
            if (field.isEmpty()) {
                field = (url + title).trim()
            }
            return field
        }

    var cover: String? = null

    //最近阅读页的地址
    var url: String = ""
    var lastUrl: String? = null

    //手动输入的标题
    var title: String = ""

    //最近阅读的标题
    var lastTitle: String = ""

    // 书籍更新时间
    var updateTime = System.currentTimeMillis()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WebBook

        if (updateTime != other.updateTime) return false
        if (cover != other.cover) return false
        if (url != other.url) return false
        if (lastUrl != other.lastUrl) return false
        if (title != other.title) return false
        if (lastTitle != other.lastTitle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = updateTime.hashCode()
        result = 31 * result + (cover?.hashCode() ?: 0)
        result = 31 * result + url.hashCode()
        result = 31 * result + (lastUrl?.hashCode() ?: 0)
        result = 31 * result + title.hashCode()
        result = 31 * result + lastTitle.hashCode()
        return result
    }

}