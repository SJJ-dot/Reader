package com.sjianjun.reader.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1
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
    .fallbackToDestructiveMigration()
    .setQueryExecutor(queryExecutor)
    .setTransactionExecutor(queryExecutor)
    .build()


val transactionDispatcher = transactionExecutor.asCoroutineDispatcher()
// setTransactionExecutor
suspend fun <T> transaction(block: suspend CoroutineScope.() -> T): T {
    return withContext(transactionDispatcher + handler) {
        db.runInTransaction(Callable {
            runBlocking(block = block)
        })
    }
}