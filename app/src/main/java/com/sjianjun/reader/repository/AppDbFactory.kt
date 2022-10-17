package com.sjianjun.reader.repository

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sjianjun.reader.App
import com.sjianjun.reader.utils.AppDirUtil
import sjj.alog.Log

object AppDbFactory {

    fun room(): AppDb {
        val room = Room.databaseBuilder(App.app, AppDb::class.java, AppDirUtil.APP_DATABASE_FILE)
            .addMigrations(object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
//                    ALTER TABLE 表名 ADD COLUMN 列名 数据类型
                    database.execSQL("ALTER TABLE 'ReadingRecord' ADD COLUMN `offest` INTEGER NOT NULL default 0")
                }
            })
            .addMigrations(object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
//                    ALTER TABLE 表名 ADD COLUMN 列名 数据类型
                    database.execSQL("ALTER TABLE 'ReadingRecord' ADD COLUMN `isEnd` INTEGER NOT NULL default 0")
                }
            })
            .addMigrations(object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE 'JavaScript' ADD COLUMN `version` INTEGER NOT NULL default 0")
                }
            })
            .addMigrations(object : Migration(4, 5) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE 'ReadingRecord' ADD COLUMN `startingStationBookSource` TEXT NOT NULL default ''")
                    database.execSQL("ALTER TABLE 'JavaScript' ADD COLUMN `isStartingStation` INTEGER NOT NULL default 0")
                    database.execSQL("ALTER TABLE 'JavaScript' ADD COLUMN `priority` INTEGER NOT NULL default 0")
                }
            })
            .addMigrations(object : Migration(5, 6) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE 'JavaScript' ADD COLUMN `supportBookCity` INTEGER NOT NULL default 0")
                }
            })
            .addMigrations(object : Migration(6, 7) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("DROP INDEX index_Book_author_title_source")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_Book_author_title_source` ON `Book` (`author`, `title`, `source`)")
                }
            })
            .addMigrations(object : Migration(7, 8) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE 'Book' ADD COLUMN `error` TEXT")
                }
            })
            .addMigrations(object : Migration(8, 9) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE 'JavaScript' ADD COLUMN `adBlockJs` TEXT NOT NULL default ''")
                    database.execSQL("ALTER TABLE 'JavaScript' ADD COLUMN `adBlockVersion` INTEGER NOT NULL default 0")
                }
            })
            .addMigrations(object : Migration(9, 10) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("DROP TABLE 'JavaScript'")
                }
            })
            .build()
        return room
    }

    @JvmStatic
    val db: AppDb = room()
}