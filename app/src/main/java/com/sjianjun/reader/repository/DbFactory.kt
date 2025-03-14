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
    version = 16,
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
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE 'ChapterContent' ADD COLUMN `contentError` INTEGER NOT NULL default 0")
                }
            })
            .addMigrations(object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE 'BookSource' ADD COLUMN `lauanage` TEXT NOT NULL default 'js'")
                }
            })
            .addMigrations(object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    //ReadingRecord 删除 bookAuthor字段
                    db.execSQL(
                        "CREATE TABLE `ReadingRecord_new` (`bookTitle` TEXT NOT NULL," +
                                " `bookId` TEXT NOT NULL," +
                                " `chapterIndex` INTEGER NOT NULL," +
                                " `offest` INTEGER NOT NULL," +
                                " `isEnd` INTEGER NOT NULL," +
                                " PRIMARY KEY(`bookTitle`))"
                    )
                    db.execSQL(
                        "INSERT INTO ReadingRecord_new (bookTitle,bookId,chapterIndex,offest,isEnd) " +
                                "SELECT bookTitle,bookId,chapterIndex,offest,isEnd FROM ReadingRecord"
                    )
                    db.execSQL("DROP TABLE ReadingRecord")
                    db.execSQL("ALTER TABLE ReadingRecord_new RENAME TO ReadingRecord")
                }
            })
            .addMigrations(object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE 'ReadingRecord' ADD COLUMN `updateTime` INTEGER NOT NULL default 0")
                }
            })
            .build()
        return room
    }

    @JvmStatic
    val db: Db = room()
}