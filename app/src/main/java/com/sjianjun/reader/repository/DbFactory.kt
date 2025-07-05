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
import com.sjianjun.reader.repository.dao.WebBookDao
import java.io.File

@Database(
    entities = [Book::class, SearchHistory::class, Chapter::class, ChapterContent::class, ReadingRecord::class, BookSource::class, WebBook::class],
    version = 20,
    exportSchema = false
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Dao
    abstract fun webBookDao(): WebBookDao
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
            .addMigrations(object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    //ChapterContent 添加 pageIndex 字段，添加 nextPageUrl 字段，修改联合主键
                    db.execSQL(
                        "CREATE TABLE `ChapterContent_new` (`chapterIndex` INTEGER NOT NULL," +
                                " `bookId` TEXT NOT NULL," +
                                " `content` TEXT," +
                                " `contentError` INTEGER NOT NULL DEFAULT 0," +
                                " `pageIndex` INTEGER NOT NULL DEFAULT 0," +
                                " `nextPageUrl` TEXT," +
                                " PRIMARY KEY(`chapterIndex`, `bookId`, `pageIndex`))"
                    )
                    db.execSQL(
                        "INSERT INTO ChapterContent_new (chapterIndex, bookId, content, contentError, pageIndex, nextPageUrl) " +
                                "SELECT chapterIndex, bookId, content, contentError, 0, NULL FROM ChapterContent"
                    )
                    db.execSQL("DROP TABLE ChapterContent")
                    db.execSQL("ALTER TABLE ChapterContent_new RENAME TO ChapterContent")
                }
            })
            .addMigrations(object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    //添加表
                    db.execSQL("CREATE TABLE IF NOT EXISTS `WebBook` (`id` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `updateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                }
            })
            .addMigrations(object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    //添加字段WebBook lastTitle
                    db.execSQL("ALTER TABLE 'WebBook' ADD COLUMN `lastTitle` TEXT NOT NULL default ''")
                }
            })
            .addMigrations(object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    //添加字段WebBook lastTitle
                    db.execSQL("ALTER TABLE 'WebBook' ADD COLUMN `cover` TEXT")
                }
            })
            .build()
        return room
    }

    @JvmStatic
    val db: Db = room()
}