package com.sjianjun.reader.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.*
import com.sjianjun.reader.repository.dao.Dao
import java.io.File

@Database(
    entities = [Book::class, SearchHistory::class, Chapter::class, ChapterContent::class, ReadingRecord::class, BookSource::class],
    version = 14,
    exportSchema = false
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Dao
}

object DbFactory {

    fun room(): Db {
        val newDbDir = App.app.getDir("database", Context.MODE_PRIVATE)
        val dbFile = File(newDbDir, "app_database").absolutePath
        val room = Room.databaseBuilder(App.app, Db::class.java, dbFile)
            .fallbackToDestructiveMigration()
            .addMigrations(object : Migration(12, 13) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE 'ChapterContent' ADD COLUMN `contentError` INTEGER NOT NULL default 0")
                }
            })
            .addMigrations(object : Migration(13, 14) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE 'BookSource' ADD COLUMN `lauanage` TEXT NOT NULL default 'js'")
                }
            })
            .build()
        return room
    }

    @JvmStatic
    val db: Db = room()
}