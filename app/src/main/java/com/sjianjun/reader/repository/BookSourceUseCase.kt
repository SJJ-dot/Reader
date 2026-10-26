package com.sjianjun.reader.repository

import android.util.Base64
import android.widget.Toast
import androidx.core.net.toUri
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.SourceUrl
import com.sjianjun.reader.http.http
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.lib.icu4j.CharsetDetector
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import sjj.alog.Log
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

object BookSourceUseCase {
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()
    suspend fun autoImport() = withIo {
        val list = SourceUrlUseCase.getAutoUpdate()
        import(list)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun import(urls: List<SourceUrl>): Int = withIo {

        val sources = urls.map { sourceUrl ->
            async {
                try {
                    val url = sourceUrl.url.trim()
                    var body = if (url.startsWith("http")) {
                        val resp = http.get(url)
                        if (resp.code != 200) {
                            Log.i("书源下载失败:${sourceUrl.group}:${sourceUrl.url}:HTTP${resp.code}")
                            return@async emptyList<BookSource>()
                        }
                        resp.body
                    } else {
                        val buffer = App.app.contentResolver.openInputStream(url.toUri())!!.readBytes()
                        val name = CharsetDetector().setText(buffer).detect()?.name ?: "UTF_8"
                        val charset = Charset.forName(name)
                        String(buffer, charset)
                    }

                    if (url.endsWith("gzip")) {
                        body = GZIPInputStream(Base64.decode(body, Base64.NO_WRAP).inputStream()).reader().readText()
                    }
                    val dest = mutableListOf<BookSource>()
                    parsePySource(sourceUrl, body, dest)
                    dest
                } catch (e: Exception) {
                    Log.i("书源导入失败:${sourceUrl}:${e.message}")
                    emptyList()
                }
            }
        }.awaitAll().flatten().toMutableList()
        val total = sources.size
        val countInvalid = sources.count { it.comment.contains("无效书源") }
        sources.removeAll { it.comment.contains("无效书源") }
        val allJs = bookSourceDao.getAllBookSource()
        val updates = sources.filter { s ->
            val local = allJs.find { it.id == s.id }
            if (local != null && local.version <= s.version && local != s || (local?.enable == true && !s.enable)) {
                s.checkResult = local.checkResult
                s.checkErrorMsg = local.checkErrorMsg
                return@filter true
            } else {
                return@filter false
            }
        }
        val newSource = sources.filter { s ->
            val local = allJs.find { it.id == s.id }
            local == null
        }
        if (updates.isEmpty() && newSource.isEmpty()) {
            Log.i("书源无变化：${total}个书源,跳过${countInvalid}个无效书源")
            return@withIo sources.size
        }
        bookSourceDao.insertBookSource(listOf(*updates.toTypedArray(), *newSource.toTypedArray()))
        toast(
            "新增${newSource.size}、更新${updates.size},跳过${countInvalid}个",
            Toast.LENGTH_LONG
        )
        Log.i("${sources.first().group}：新增${newSource.size}、更新${updates.size}")
        return@withIo sources.size
    }


}

fun parsePySource(sourceUrl: SourceUrl, body: String, dest: MutableList<BookSource>): Boolean {
    try {
        if (!body.contains("pySource")) {
            Log.i("不是修仙py书源")
            return false
        }
        val source = gson.fromJson<Source>(body)
        source?.pySource?.forEach { pySource ->
            dest.add(
                BookSource().apply {
                    name = pySource.source!!
                    group = sourceUrl.group
                    js = pySource.js!!
                    version = pySource.version
                    enable = pySource.enable
                    requestDelay = pySource.requestDelay
                    lauanage = BookSource.Language.py
                    comment = pySource.comment ?: ""
                }
            )

        }
        Log.i("解析书源成功：${sourceUrl.group}，共${dest.size}个书源")
        return true
    } catch (e: Exception) {
        Log.e("解析书源失败", e)
        return false
    }
}




class Source {
    var pySource: List<PySource>? = null

    class PySource {
        var comment: String? = null
        var source: String? = null
        var js: String? = null
        var version: Long = -1
        var enable: Boolean = true
        var requestDelay: Long = -1
    }
}
