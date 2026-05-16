package com.sjianjun.reader.bean

data class SourceUrl(
    val url: String,
    val group: String,
    var autoUpdate: Boolean = false
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