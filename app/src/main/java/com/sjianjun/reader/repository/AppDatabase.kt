package com.sjianjun.reader.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.*
import com.sjianjun.reader.utils.withIo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import sjj.alog.Log
import java.util.concurrent.Callable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Database(
    entities = [Book::class, JavaScript::class, SearchHistory::class, Chapter::class, ReadingRecord::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): Dao
}

val db = Room.databaseBuilder(
    App.app,
    AppDatabase::class.java, "app_database"
).build()


suspend inline fun <T> transaction(
    context: CoroutineContext = EmptyCoroutineContext,
    noinline block: suspend CoroutineScope.() -> T
): T {
    return withIo {
        db.runInTransaction(Callable {
            runBlocking(context, block)
        })
    }
}