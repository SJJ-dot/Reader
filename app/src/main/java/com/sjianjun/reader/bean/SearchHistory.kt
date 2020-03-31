package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SearchHistory(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    var query: String = ""
)