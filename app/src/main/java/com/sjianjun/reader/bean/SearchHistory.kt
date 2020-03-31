package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SearchHistory(
    @PrimaryKey
    var query: String = ""
)