package com.sjianjun.reader.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ChapterContent
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.bean.ReplacementRule
import com.sjianjun.reader.bean.SearchHistory
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.dao.BookDao
import com.sjianjun.reader.repository.dao.BookSourceDao
import com.sjianjun.reader.repository.dao.ChapterContentDao
import com.sjianjun.reader.repository.dao.ChapterDao
import com.sjianjun.reader.repository.dao.ReadingRecordDao
import com.sjianjun.reader.repository.dao.ReplacementRuleDao
import com.sjianjun.reader.repository.dao.SearchHistoryDao
import com.sjianjun.reader.repository.dao.WebBookDao
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [Book::class, SearchHistory::class, Chapter::class, ChapterContent::class, ReadingRecord::class, BookSource::class, WebBook::class, ReplacementRule::class],
    version = 24,
    exportSchema = false
)
abstract class Db : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chapterContentDao(): ChapterContentDao
    abstract fun readingRecordDao(): ReadingRecordDao
    abstract fun bookSourceDao(): BookSourceDao
    abstract fun webBookDao(): WebBookDao
    abstract fun replacementRule(): ReplacementRuleDao
}

object DbFactory {

    const val DB_NAME = "app_database"
    const val BACKUP_DB_NAME = "reader-database-backup"

    @Volatile
    private var instance: Db? = null

    val db: Db
        get() = room()

    @Synchronized
    fun room(): Db {
        instance?.let { return it }
        val dbFile = getDatabaseFile().absolutePath
        return buildDatabase(dbFile).also { instance = it }
    }

    private fun buildDatabase(dbFile: String): Db {
        return Room.databaseBuilder(App.app, Db::class.java, dbFile)
            .fallbackToDestructiveMigration(true)
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
                    db.execSQL("CREATE TABLE IF NOT EXISTS `WebBook` (`id` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `updateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                }
            })
            .addMigrations(object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE 'WebBook' ADD COLUMN `lastTitle` TEXT NOT NULL default ''")
                }
            })
            .addMigrations(object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE 'WebBook' ADD COLUMN `cover` TEXT")
                }
            })
            .addMigrations(object : Migration(20, 21) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE 'WebBook' ADD COLUMN `lastUrl` TEXT")
                }
            })
            .addMigrations(object : Migration(21, 22) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE 'ReadingRecord' ADD COLUMN `scrollOffset` INTEGER NOT NULL default 0")
                }
            })
            .addMigrations(object : Migration(22, 23) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `ReplacementRule` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `rule` TEXT NOT NULL, `replacement` TEXT NOT NULL, `isRegex` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `order` INTEGER NOT NULL, `applyToTitle` INTEGER NOT NULL, `applyToContent` INTEGER NOT NULL, `scope` TEXT, `excludeScope` TEXT, PRIMARY KEY(`id`))")
                }
            })
            .addMigrations(object : Migration(23, 24) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE 'ReadingRecord' ADD COLUMN `bookCover` TEXT")
                    db.execSQL("ALTER TABLE 'ReadingRecord' ADD COLUMN `bookIntro` TEXT")
                }
            })
            .build()
    }

    fun getInternalDatabaseDir(): File = App.app.getDir("database", Context.MODE_PRIVATE)

    fun getDatabaseFile(): File {
        val path = globalConfig.databaseStorageDir
        val dir = if (path.isNullOrBlank()) {
            getInternalDatabaseDir()
        } else {
            File(path)
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, DB_NAME)
    }

    @Synchronized
    fun closeDatabase() {
        instance?.close()
        instance = null
    }

    @Synchronized
    fun switchDatabaseStorageToDirectory(targetDir: File, useExistingDatabase: Boolean): Boolean {
        requireDirectoryAccess(targetDir)
        val oldDb = getDatabaseFile()
        val targetDb = File(targetDir, DB_NAME)
        if (oldDb.absoluteFile == targetDb.absoluteFile) {
            return false
        }
        prepareDatabaseForFileTransfer()
        if (useExistingDatabase) {
            if (!targetDb.exists()) {
                error("所选目录中不存在数据库文件")
            }
            validateSqliteFile(targetDb)
        } else {
            oldDb.copyTo(targetDb, overwrite = true)
            deleteSidecarFiles(targetDb)
        }
        if (getInternalDatabaseDir().absoluteFile == targetDir.absoluteFile) {
            globalConfig.databaseStorageDir = null
        } else {
            globalConfig.databaseStorageDir = targetDir.absolutePath
        }
        closeDatabase()
        deleteSidecarFiles(oldDb)
        return true
    }


    @Synchronized
    fun exportDatabaseToShareFile(): File {
        prepareDatabaseForFileTransfer()
        val sourceDb = getDatabaseFile()
        val shareDir = File(App.app.cacheDir, "share/database").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        shareDir.listFiles()?.forEach { file ->
            file.delete()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val shareFile = File(shareDir, "$BACKUP_DB_NAME-$timestamp.db")
        deleteSidecarFiles(shareFile)
        sourceDb.copyTo(shareFile, overwrite = true)
        return shareFile
    }

    @Synchronized
    fun importDatabaseFromUri(uri: Uri) {
        val targetDb = getDatabaseFile()
        val backupFile = File(targetDb.parentFile, "$DB_NAME.backup")
        val tempFile = File(targetDb.parentFile, "$DB_NAME.import")
        App.app.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("无法读取导入文件")
        validateSqliteFile(tempFile)
        prepareDatabaseForFileTransfer()
        if (targetDb.exists()) {
            targetDb.copyTo(backupFile, overwrite = true)
        }
        deleteSidecarFiles(targetDb)
        try {
            tempFile.copyTo(targetDb, overwrite = true)
            backupFile.delete()
        } catch (e: Exception) {
            if (backupFile.exists()) {
                backupFile.copyTo(targetDb, overwrite = true)
            }
            throw e
        } finally {
            deleteSidecarFiles(tempFile)
            tempFile.delete()
            backupFile.delete()
        }
        closeDatabase()
    }

    private fun prepareDatabaseForFileTransfer() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use {
            // no-op
        }
        closeDatabase()
        val dbFile = getDatabaseFile()
        if (!dbFile.exists()) {
            error("数据库文件不存在")
        }
    }

    private fun validateSqliteFile(file: File) {
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        database.use {
            it.rawQuery("SELECT name FROM sqlite_master WHERE type='table' LIMIT 1", null).use { cursor ->
                if (!cursor.moveToFirst()) {
                    error("导入文件不是有效的数据库")
                }
            }
        }
    }

    private fun deleteSidecarFiles(dbFile: File) {
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            File(dbFile.absolutePath + suffix).delete()
        }
    }

    private fun requireDirectoryAccess(directory: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            error("缺少文件管理权限")
        }
        if (!directory.exists() && !directory.mkdirs()) {
            error("无法创建所选目录")
        }
    }
}
