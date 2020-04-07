package com.sjianjun.reader.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.*
import com.sjianjun.reader.utils.withIo
import com.sjianjun.reader.utils.withSingle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable

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


suspend inline fun <T> transaction(noinline block: suspend CoroutineScope.() -> T): T {
    return withSingle {
        db.runInTransaction(Callable {
            runBlocking(block = block)
        })
    }
}