package com.sjianjun.reader.repository

import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.preferences.JsConfig

object JsManager {
    private var allJs: List<JavaScript>? = null
    suspend fun getAllJs(): List<JavaScript> = withIo {
        if (allJs == null) {
            allJs = JsConfig.allJsSource.mapNotNull { source ->
                JsConfig.getJs(source)
            }
        }
        allJs ?: emptyList()
    }

    suspend fun getAllStartingJs(): List<JavaScript> {
        return getAllJs().filter { it.isStartingStation }
    }

    suspend fun getJs(source: String): JavaScript? {
        return getAllJs().find { it.source == source }
    }

    suspend fun getAllBookJs(bookTitle: String, bookAuthor: String): List<JavaScript> {
        val list = db.dao().getAllBookSource(bookTitle, bookAuthor)
        return getAllJs().filter { list.contains(it.source) }
    }

    fun deleteJs(source: String) {
        JsConfig.removeJs(source)
        JsConfig.allJsSource = JsConfig.allJsSource.toMutableList().apply {
            remove(source)
        }
        allJs = allJs?.toMutableList()?.apply {
            removeAll { it.source == source }
        }
    }

    fun saveJs(js: JavaScript, version: Int) {
        JsConfig.saveJs(js.source, js)
        JsConfig.saveJsVersion(js.source, version)
        JsConfig.allJsSource.let {
            if (!it.contains(js.source)) {
                JsConfig.allJsSource = it.toMutableList().apply {
                    add(js.source)
                }
            }
        }
        allJs?.toMutableList()?.let {
            if (!it.contains(js)) {
                it.add(js)
            }
            allJs = it
        }
    }


}