package com.sjianjun.reader.repository

import android.util.Base64
import android.widget.Toast
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.URL_BOOK_SOURCE_URL_LIST
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import sjj.alog.Log
import java.util.zip.GZIPInputStream

object BookSourceMgr {
    private val dao = DbFactory.db.dao()
    suspend fun getAllBookSource(): List<BookSource> = withIo {
        dao.getAllBookSource().first()
    }

    suspend fun getAllEnableBookSource(): List<BookSource> = withIo {
        dao.getAllEnableBookSource().first()
    }

    suspend fun getBookSourceById(sourceId: String) = withIo {
        dao.getBookSourceById(sourceId)
    }

    suspend fun getBookBookSource(bookTitle: String, bookAuthor: String) = withIo {
        dao.getBookBookSource(bookTitle, bookAuthor)
    }

    suspend fun saveJs(vararg js: BookSource) = withIo {
        dao.insertBookSource(js.asList())
    }

    suspend fun delete(vararg js: BookSource) = withIo {
        dao.deleteBookSource(js.asList())
    }

    suspend fun check(js: BookSource, key: String) = withIo {
        val search: SearchResult
        try {
            search = js.search(key)!!.first()
        } catch (e: Exception) {
            js.checkResult = "校验失败：搜索出错"
            js.checkErrorMsg = e.stackTraceToString()
            Log.e("${js.group}-${js.name}:${js.checkResult}", e)
            return@withIo
        }
        val details: Book
        try {
            details = js.getDetails(search.bookUrl)!!
        } catch (e: Exception) {
            js.checkResult = "校验失败：详情加载失败"
            js.checkErrorMsg = e.stackTraceToString()
            Log.e("${js.group}-${js.name}:${js.checkResult}", e)
            return@withIo
        }
        try {
            val chapter = details.chapterList?.firstOrNull()!!
            val content = js.getChapterContent(chapter.url)
            assert(content!!.isNotBlank())
        } catch (e: Exception) {
            js.checkResult = "校验失败：章节内容加载失败"
            js.checkErrorMsg = e.stackTraceToString()
            Log.e("${js.group}-${js.name}:${js.checkResult}", e)
            return@withIo
        }
        js.checkResult = "校验成功"
        js.checkErrorMsg = null
    }.apply {
        dao.insertBookSource(listOf(js))
    }

    private suspend fun fetchSourceUrl() = withIo {
        try {
            val list = gson.fromJson<List<String>>(http.get(URL_BOOK_SOURCE_URL_LIST).body)
            if (!list.isNullOrEmpty()) {
                globalConfig.bookSourceImportUrlsNet = list
            }
            Log.i("书源地址导入成功")
        } catch (e: Exception) {
            Log.e("书源地址导入失败", e)
        }
    }

    suspend fun autoImport() = withIo {
        fetchSourceUrl()
        import(globalConfig.bookSourceImportUrlsNet)
        import(globalConfig.bookSourceImportUrlsLoc)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun import(urls: List<String>) = withIo {

        val sources = urls.map { url ->
            async {
                try {
                    var body = http.get(url).body
                    if (url.endsWith("gzip")) {
                        body = GZIPInputStream(
                            Base64.decode(body, Base64.NO_WRAP).inputStream()
                        ).reader().readText()
                    }
                    val source = JSONObject(body)
                    val bookSourceArray = source.getJSONArray("bookSource")
                    (0 until bookSourceArray.length()).map {
                        val obj = bookSourceArray.getJSONObject(it)
                        BookSource().apply {
                            name = obj.getString("source")
                            group = source.optString("group")
                            js = obj.getString("js")
                            version = obj.optInt("js", -1)
                            enable = obj.optBoolean("enable", true)
                            requestDelay = obj.optLong("requestDelay", -1)
                        }
                    }
                } catch (e: Exception) {
                    Log.i("书源导入失败:${url}",e)
                    emptyList()
                }
            }
        }.awaitAll().flatten()


        val allJs = getAllBookSource()
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
            return@withIo
        }
        saveJs(*updates.toTypedArray(), *newSource.toTypedArray())

        toast("${sources.first().group}：新增${newSource.size}、更新${updates.size}", Toast.LENGTH_LONG)
        Log.i("${sources.first().group}：新增${newSource.size}、更新${updates.size}")
    }

}