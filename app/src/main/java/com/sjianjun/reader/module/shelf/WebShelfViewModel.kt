package com.sjianjun.reader.module.shelf

import androidx.lifecycle.ViewModel
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.http.WebViewClient
import com.sjianjun.reader.repository.DbFactory.db
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.toast
import io.legado.app.help.http.BackstageWebView.WebViewResponse
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import sjj.alog.Log

class WebShelfViewModel : ViewModel() {
    private val dao = db.webBookDao()

    suspend fun getWebBook(): Flow<List<WebBook>> = withIo {
        dao.getWebBook()
    }

    suspend fun deleteWebBook(webBook: WebBook) = withIo {
        dao.deleteWebBookById(webBook.id)
    }

    suspend fun insertWebBook(webBook: WebBook) = withIo {
        dao.insertWebBook(webBook)
    }

    suspend fun getWebBookById(id: String): Flow<WebBook> = withIo {
        dao.getWebBookById(id)
    }

    suspend fun importWebBook(webBook: WebBook) = withIo {
        var resp: WebViewResponse? = null
        try {
            resp = gson.fromJson<WebViewResponse>(WebViewClient.get(webBook.url))
            val document = Jsoup.parse(resp!!.html, resp.url)
            var cover = document.select("meta[property='og:image']").firstOrNull()?.attr("content")
            if (cover.isNullOrEmpty()) {
                cover = document.select("#fmimg").firstOrNull()?.absUrl("src")
            }
            if (cover.isNullOrEmpty()) {
                cover = document.select("img").firstOrNull {
                    it.attr("alt").contains(webBook.title)
                            || it.attr("title").contains(webBook.title)
                            || it.attr("src").contains("cover")
                }?.absUrl("src")
            }
            if (cover.isNullOrEmpty()) {
                val imgs = document.select("img").filter {
                    val src = it.attr("src")
                    src.endsWith("jpg") || src.endsWith("jpeg")
                }
                cover = imgs.firstOrNull {
                    (it.attr("width").toIntOrNull() ?: 0) in 100..200
                }?.absUrl("src") ?: imgs.firstOrNull()?.absUrl("src")
            }
            if (cover.isNullOrEmpty()) {
                Log.e("封面获取失败, url: ${webBook.url}")
                webBook.cover = webBook.url.toHttpUrlOrNull()?.let { httpUrl ->
                    "${httpUrl.scheme}://${httpUrl.host}/favicon.ico"
                }
            } else {
                webBook.cover = cover
            }
            Log.i("封面获取成功: ${webBook.cover}, url: ${webBook.url}")
        } catch (e: Exception) {
            Log.e(resp?.html ?: "获取封面失败", e)
            Log.e("封面获取失败 ${webBook.url}", e)
            toast("封面获取失败: ${e.message ?: "未知错误"}")
        }
        dao.insertWebBook(webBook)
    }
}