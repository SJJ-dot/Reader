package com.sjianjun.reader.manager

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.JavaScript

@Database(entities = [Book::class, JavaScript::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): Dao
}

val db = Room.databaseBuilder(
    App.app,
    AppDatabase::class.java, "app_database"
).build()
