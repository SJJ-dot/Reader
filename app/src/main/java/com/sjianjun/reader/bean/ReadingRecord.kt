package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index("bookTitle","bookAuthor", unique = true)])
class ReadingRecord {
    @PrimaryKey(autoGenerate = true)
    var id = 0
    var bookTitle: String = ""
    var bookAuthor: String = ""

    var readingBookId = 0

    var readingBookChapterId = 0
}