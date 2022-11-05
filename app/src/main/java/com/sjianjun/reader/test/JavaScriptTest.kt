package com.sjianjun.reader.test

import android.content.res.AssetManager
import android.util.Base64
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.App
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.BookSource.Func.*
import com.sjianjun.reader.bean.ChapterContent
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.http.http
import com.sjianjun.reader.utils.URL_BOOK_SOURCE_DEF
import org.json.JSONArray
import org.json.JSONObject
import sjj.alog.Log
import java.nio.charset.Charset

fun getAssetsTxt(name: String): String {
    return App.app.assets.open(name, AssetManager.ACCESS_BUFFER).use { stream ->
        stream.bufferedReader().readText()
    }
}

/**
 * 已解析：biquge5200.cc、古古小说网（55小说网）
 * 不支持：乐安宣书网(搜索结果没有作者)、
 */
object JavaScriptTest {
    var test = false
    val javaScript by lazy {
        BookSource().apply {
            js = getAssetsTxt("js/BookSourceLegado.js")
        }
    }


    suspend fun simpleSource() {
        val source = http.get("https://raw.iqiq.io/XIU2/Yuedu/master/shuyuan").body
        val newJson = JSONArray()
        val jsonArray = JSONArray(source)
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val newObj = JSONObject()
            newObj.put("bookSourceName", jsonObject.get("bookSourceName"))
            newObj.put("bookSourceUrl", jsonObject.get("bookSourceUrl"))
            newObj.put("ruleBookInfo", jsonObject.get("ruleBookInfo"))
            newObj.put("ruleContent", jsonObject.get("ruleContent"))
            newObj.put("ruleSearch", jsonObject.get("ruleSearch"))
            newObj.put("ruleToc", jsonObject.get("ruleToc"))
            newJson.put(newObj)
        }
    }

    suspend fun testJavaScript() = withIo {
//        javaScript.jsProps.add("rule" to JSONArray(getAssetsTxt("js/hh.json")).getJSONObject(0).toString())
//        javaScript.search("我的")
////        Log.e(base.newBuilder("/absaa/html.hh"))
////        val base11 = HttpUrl.get("https://raw.iqiq.io/XIU2/Yuedu/master/shuyuan")
////        Log.e(base11.newBuilder("aa/html.hh"))
////        Log.e(base.newBuilder("/absaa/html.hh"))
////        HttpUrl.Builder().host()
        if (!test || !BuildConfig.DEBUG) {
            return@withIo
        }
        val query = "诡秘之主"
//        val query = "哈利波特"
        Log.e("${javaScript.name} 搜索 $query")
        val result = javaScript.execute<List<SearchResult>>(search, query)
        Log.e(result)
        if (result.isNullOrEmpty()) {
            Log.e("${javaScript.name} 搜索结果为空")
            return@withIo
        }
        val first = result.first()
        Log.e("${first.source} bookCount:${result.size} 加载书籍详情 ${first.bookTitle} ${first.bookUrl}")
        val book = javaScript.execute<Book>(getDetails, first.bookUrl)
        if (book == null) {
            Log.e("${first.source}  书籍加载失败")
            return@withIo
        }
        Log.e("${book.title} chapterCount:${book.chapterList?.size} 加载章节内容:${book.chapterList?.firstOrNull()?.title} $book")
        val chapter = book.chapterList?.firstOrNull()
        if (chapter != null) {
            val c = javaScript.execute<String>(getChapterContent, chapter.url)
            chapter.content = ChapterContent(chapter.url, chapter.index, c ?: "")
            Log.e("测试：${if (c.isNullOrBlank()) "失败" else "通过"} ${chapter.content} ")
        }
        Unit
    }
}