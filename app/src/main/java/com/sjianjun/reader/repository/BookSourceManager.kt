package com.sjianjun.reader.repository

import android.util.Base64
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.JsConfig
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import org.json.JSONObject
import sjj.alog.Log
import java.nio.charset.Charset

object BookSourceManager {
    suspend fun getAllJs(): List<BookSource> = withIo {
        JsConfig.getAllJs()
    }

    suspend fun getAllStartingJs(): List<BookSource> {
        return getAllJs().filter { it.isOriginal && it.enable }
    }

    fun getJs(source: String): BookSource? {
        return JsConfig.getJs(source)
    }

    suspend fun getAllBookJs(bookTitle: String, bookAuthor: String): List<BookSource> {
        val list = AppDbFactory.db.dao().getAllBookSource(bookTitle, bookAuthor)
        return getAllJs().filter { list.contains(it.source) }
    }

    fun saveJs(vararg js: BookSource) {
        JsConfig.saveJs(*js)
    }

    fun delete(vararg js: BookSource) {
        JsConfig.removeJs(*js.map { it.source }.toTypedArray())
    }

    suspend fun check(js: BookSource, key: String) {
        val search: SearchResult
        try {
            search = js.search(key)!!.first()
        } catch (e: Exception) {
            js.checkResult = "校验失败：搜索出错"
            js.checkErrorMsg = e.stackTraceToString()
            Log.e("${js.source}:${js.checkResult}", e)
            return
        }
        val details: Book
        try {
            details = js.getDetails(search.bookUrl)!!
        } catch (e: Exception) {
            js.checkResult = "校验失败：详情加载失败"
            js.checkErrorMsg = e.stackTraceToString()
            Log.e("${js.source}:${js.checkResult}", e)
            return
        }
        try {
            val chapter = details.chapterList?.firstOrNull()!!
            val content = js.getChapterContent(chapter.url)
            assert(content!!.isNotBlank())
        } catch (e: Exception) {
            js.checkResult = "校验失败：章节内容加载失败"
            js.checkErrorMsg = e.stackTraceToString()
            Log.e("${js.source}:${js.checkResult}", e)
            return
        }
        js.checkResult = "校验成功"
        js.checkErrorMsg = null
    }

    suspend fun import(url: String) = withIo {
        val source = JSONObject(http.get(url))
        val bookSourceArray = source.getJSONArray("bookSource")
        val sources = (0 until bookSourceArray.length()).map {
            val obj = bookSourceArray.getJSONObject(it)
            BookSource(
                obj.getString("source"),
                Base64.decode(obj.getString("js"), Base64.NO_WRAP)
                    .toString(Charset.forName("utf-8")),
                obj.getInt("version"),
                obj.getBoolean("original"),
                obj.getBoolean("enable"),
                source.getString("group"),
                obj.getLong("requestDelay"),
                obj.getString("website"),
            )
        }
        saveJs(*sources.toTypedArray())
    }

}