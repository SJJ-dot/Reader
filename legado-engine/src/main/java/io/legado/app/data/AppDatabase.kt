package io.legado.app.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.legado.app.data.dao.CacheDao
import io.legado.app.data.dao.CookieDao
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.Cookie
import splitties.init.appCtx

val appDb by lazy {
    Room.databaseBuilder(appCtx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
        .fallbackToDestructiveMigration()
        .allowMainThreadQueries()
        .build()
}

@Database(
    version = 1,
    exportSchema = false,
    entities = [Cookie::class, Cache::class],
)
abstract class AppDatabase : RoomDatabase() {

    abstract val cookieDao: CookieDao
    abstract val cacheDao: CacheDao

    companion object {

        const val DATABASE_NAME = "legado.db"
    }

}