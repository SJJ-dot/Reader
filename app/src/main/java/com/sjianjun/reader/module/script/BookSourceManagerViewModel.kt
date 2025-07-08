package com.sjianjun.reader.module.script

import androidx.lifecycle.ViewModel
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.repository.DbFactory
import sjj.alog.Log

class BookSourceManagerViewModel : ViewModel() {
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()
    suspend fun check(js: BookSource, query: String) = withIo {
        val search: SearchResult
        try {
            search = js.search(query)!!.first()
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

        if (search.bookTitle != details.title) {
            js.checkResult = "校验失败：书名、作者发生变化"
            js.checkErrorMsg = "搜索结果书名与详情页不同：${search.bookTitle}==>${details.title}_${details.author}"
            return@withIo
        }

        try {
            val chapter = details.chapterList?.firstOrNull()!!
            val content = js.getChapterContent(chapter.url)
            assert(!content.contentError)
        } catch (e: Exception) {
            js.checkResult = "校验失败：章节内容加载失败"
            js.checkErrorMsg = e.stackTraceToString()
            Log.e("${js.group}-${js.name}:${js.checkResult}", e)
            return@withIo
        }
        js.checkResult = "校验成功"
        js.checkErrorMsg = null
    }.apply {
        bookSourceDao.insertBookSource(listOf(js))
    }
}