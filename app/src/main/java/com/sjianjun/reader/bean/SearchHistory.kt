package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SearchHistory(
    @PrimaryKey
    @JvmField
    var query: String

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchHistory

        if (query != other.query) return false

        return true
    }

    override fun hashCode(): Int {
        return query.hashCode()
    }
}