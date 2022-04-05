package com.sjianjun.reader.repository

import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.preferences.JsConfig

object JsManager {
    suspend fun getAllJs(): List<JavaScript> = withIo {
        JsConfig.getAllJs()
    }

    suspend fun getAllStartingJs(): List<JavaScript> {
        return getAllJs().filter { it.isStartingStation && it.enable }
    }

    fun getJs(source: String): JavaScript? {
        return JsConfig.getJs(source)
    }

    suspend fun getAllBookJs(bookTitle: String, bookAuthor: String): List<JavaScript> {
        val list = AppDbFactory.db.dao().getAllBookSource(bookTitle, bookAuthor)
        return getAllJs().filter { list.contains(it.source) }
    }

    fun saveJs(js: JavaScript) {
        JsConfig.saveJs(js)
    }


}