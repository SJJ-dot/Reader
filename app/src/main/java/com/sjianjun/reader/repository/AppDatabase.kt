package com.sjianjun.reader.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.*
import com.sjianjun.reader.utils.withSingle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Database(
    entities = [Book::class, JavaScript::class, SearchHistory::class, Chapter::class, ChapterContent::class, ReadingRecord::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): Dao
}

private val threadId = AtomicInteger(0)
private val transaction = Executors.newFixedThreadPool(1) { r ->
    Thread(r, String.format("room_io_%d", threadId.getAndIncrement()))
}

val db = Room.databaseBuilder(App.app, AppDatabase::class.java, "app_database")
    .fallbackToDestructiveMigration()
//    .setQueryExecutor(transaction)
//    .setTransactionExecutor(transaction)
    .build()


suspend fun <T> transaction(block: suspend CoroutineScope.() -> T): T {
    return withSingle {
        db.runInTransaction(Callable {
            runBlocking(block = block)
        })
    }
}