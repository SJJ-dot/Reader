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
    entities = [Book::class, SearchHistory::class, Chapter::class, ChapterContent::class, ReadingRecord::class, BookSource::class],
    version = 13,
    exportSchema = false
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Dao
}

object DbFactory {

    fun room(): Db {
        val room = Room.databaseBuilder(App.app, Db::class.java, AppDirUtil.APP_DATABASE_FILE)
            .fallbackToDestructiveMigration()
            .addMigrations(object : Migration(12, 13) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE 'ChapterContent' ADD COLUMN `contentError` INTEGER NOT NULL default 0")
                }
            })
            .build()
        return room
    }

    @JvmStatic
    val db: Db = room()
}