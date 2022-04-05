package com.sjianjun.reader.repository

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sjianjun.reader.bean.*

@Database(
    entities = [Book::class, SearchHistory::class, Chapter::class, ChapterContent::class, ReadingRecord::class],
    version = 10,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): Dao
}

