package com.sjianjun.reader.preferences

import android.content.Context
import com.sjianjun.reader.App

val globalConfig by lazy { AppConfig() }
val globalBookConfig by lazy { BookConfig() }

class AppConfig {

}

class BookConfig {

    private val config by lazy { App.app.getSharedPreferences("BookConfig", Context.MODE_PRIVATE) }


    fun getReadingChapter(bookId: Int): Int {
        return config.getInt("ReadingChapter_${bookId}", -1)
    }

    fun setReadingChapter(bookId: Int, chapterId: Int) {
        config.edit().putInt("ReadingChapter_${bookId}", chapterId).apply()
    }
}