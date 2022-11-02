package com.sjianjun.reader.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.*
import com.sjianjun.reader.repository.dao.Dao
import com.sjianjun.reader.utils.AppDirUtil

@Database(
    entities = [Book::class, SearchHistory::class, Chapter::class, ChapterContent::class, ReadingRecord::class],
    version = 11,
    exportSchema = false
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Dao
}

object DbFactory {

    fun room(): Db {
        val room = Room.databaseBuilder(App.app, Db::class.java, AppDirUtil.APP_DATABASE_FILE)
            .fallbackToDestructiveMigration()
            .build()
        return room
    }

    @JvmStatic
    val db: Db = room()
}