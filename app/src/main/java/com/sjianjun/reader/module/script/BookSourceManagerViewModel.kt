package com.sjianjun.reader.module.script

import androidx.lifecycle.ViewModel
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.repository.BookSourceUseCase
import com.sjianjun.reader.repository.DbFactory
import kotlinx.coroutines.flow.first
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

    suspend fun delete(list: List<BookSource>) = withIo {
        bookSourceDao.deleteBookSource(list)
    }

    suspend fun import(list: List<String>): Int {
        return BookSourceUseCase.import(list)
    }

    private fun List<BookSource>.sort(): List<BookSource> {
        return sortedWith { p0, p1 ->
            val g0 = p0.group
            val g1 = p1.group
            if (g0 != g1) {
                return@sortedWith g0.compareTo(g1)
            }
            if (p0.enable != p1.enable) {
                return@sortedWith if (p0.enable) -1 else 1
            }
            return@sortedWith p0.name.compareTo(p1.name)

        }
    }

    suspend fun getAllBookSource(): List<BookSource> = withIo {
        return@withIo bookSourceDao.getAllBookSource().sort()
    }

    suspend fun saveJs(list: List<BookSource>) {
        withIo {
            bookSourceDao.insertBookSource(list)
        }
    }
}