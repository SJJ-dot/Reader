package com.sjianjun.reader.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.*
import com.sjianjun.reader.utils.handler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Database(
    entities = [Book::class, JavaScript::class, SearchHistory::class, Chapter::class, ChapterContent::class, ReadingRecord::class],
    version = 7
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): Dao
}

private val threadId = AtomicInteger(0)

val transactionExecutor = Executors.newFixedThreadPool(1) { r ->
    Thread(r, String.format("transaction_%d", threadId.getAndIncrement()))
}

val queryExecutor = Executors.newFixedThreadPool(10) { r ->
    Thread(r, String.format("query_%d", threadId.getAndIncrement()))
}
val db = Room.databaseBuilder(App.app, AppDatabase::class.java, "app_database")
    .setQueryExecutor(queryExecutor)
    .setTransactionExecutor(queryExecutor)
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
    .build()


val transactionDispatcher = transactionExecutor.asCoroutineDispatcher()
// setTransactionExecutor
suspend fun <T> transaction(block: suspend CoroutineScope.() -> T): T {
    return withContext(transactionDispatcher) {
        db.runInTransaction(Callable {
            runBlocking(block = block)
        })
    }
}