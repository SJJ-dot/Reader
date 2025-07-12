package com.sjianjun.reader.repository

import android.util.Base64
import android.widget.Toast
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.URL_BOOK_SOURCE_DEF
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.name
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import sjj.alog.Log
import java.util.zip.GZIPInputStream
import kotlin.math.abs

object BookSourceUseCase {
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()
    private val chapterDao get() = DbFactory.db.chapterDao()

    suspend fun autoImport() = withIo {
        val list = globalConfig.bookSourceListUser.toMutableList()
        val def = globalConfig.bookSourceDef
        if (def.isNotBlank() || list.isEmpty()) {
            list.add(URL_BOOK_SOURCE_DEF)
        }
        import(list)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun import(urls: List<String>): Int = withIo {

        val sources = urls.map { url ->
            async {
                try {
                    val header = mapOf("Cache-Control" to "no-cache", "Pragma" to "no-cache")
                    val query = mapOf("_" to System.currentTimeMillis().toString())
                    var body = http.get(url, queryMap = query, header = header).body
                    if (url.endsWith("gzip")) {
                        body = GZIPInputStream(
                            Base64.decode(body, Base64.NO_WRAP).inputStream()
                        ).reader().readText()
                    }
                    val source = JSONObject(body)

                    val bookSourceList = mutableListOf<BookSource>()
                    val groupName = source.optString("group")

                    val bookSourceArray = source.getJSONArray("bookSource")
                    (0 until bookSourceArray.length()).mapTo(bookSourceList) {
                        val obj = bookSourceArray.getJSONObject(it)
                        BookSource().apply {
                            name = obj.getString("source")
                            group = obj.optString("group", groupName)
                            js = obj.getString("js")
                            version = obj.optInt("version", -1)
                            enable = obj.optBoolean("enable", true)
                            requestDelay = obj.optLong("requestDelay", -1)
                            lauanage = BookSource.Language.js
                        }
                    }

                    source.optJSONArray("pySource")?.also { pySourceArr ->
                        (0 until pySourceArr.length()).mapTo(bookSourceList) {
                            val obj = pySourceArr.getJSONObject(it)
                            BookSource().apply {
                                name = obj.getString("source")
                                group = obj.optString("group", groupName)
                                js = obj.getString("js")
                                version = obj.optInt("version", -1)
                                enable = obj.optBoolean("enable", true)
                                requestDelay = obj.optLong("requestDelay", -1)
                                lauanage = BookSource.Language.py
                            }
                        }
                    }
                    bookSourceList
                } catch (e: Exception) {
                    Log.i("书源导入失败:${url}", e)
                    emptyList()
                }
            }
        }.awaitAll().flatten()


        val allJs = bookSourceDao.getAllBookSource()
        val updates = sources.filter { s ->
            val local = allJs.find { it.id == s.id }
            local != null && local.version < s.version
        }
        val newSource = sources.filter { s ->
            val local = allJs.find { it.id == s.id }
            local == null
        }
        if (updates.isEmpty() && newSource.isEmpty()) {
            Log.i("书源无变化")
            return@withIo sources.size
        }
        bookSourceDao.insertBookSource(listOf(*updates.toTypedArray(), *newSource.toTypedArray()))
        toast(
            "${sources.first().group}：新增${newSource.size}、更新${updates.size}",
            Toast.LENGTH_LONG
        )
        Log.i("${sources.first().group}：新增${newSource.size}、更新${updates.size}")
        return@withIo sources.size
    }


}