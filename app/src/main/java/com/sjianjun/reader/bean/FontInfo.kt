package com.sjianjun.reader.bean

class FontInfo(var name: String? = null, var path: String? = null, var resId: Int = 0, var isAsset: Boolean = false){

    companion object {
        val DEFAULT = FontInfo("系统", resId = 0, isAsset = true)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FontInfo

        if (name != other.name) return false
        if (path != other.path) return false
        if (resId != other.resId) return false
        if (isAsset != other.isAsset) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (path?.hashCode() ?: 0)
        result = 31 * result + resId
        result = 31 * result + isAsset.hashCode()
        return result
    }
}