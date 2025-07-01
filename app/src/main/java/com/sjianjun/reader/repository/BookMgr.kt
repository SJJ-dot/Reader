package com.sjianjun.reader.repository

import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.utils.MessageException
import com.sjianjun.reader.utils.name
import kotlinx.coroutines.flow.firstOrNull
import sjj.alog.Log
import kotlin.math.abs

object BookMgr {
    private val dao = DbFactory.db.dao()

    suspend fun getBookAllSourceFlow(title: String) = withIo {
        return@withIo dao.getBookAllSourceFlow(title)
    }

    suspend fun reloadBookFromNet(book: Book?) = withIo {
        book ?: return@withIo
        try {
            val script = book.bookSource ?: dao.getBookSourceById(book.bookSourceId).firstOrNull()
            ?: throw MessageException("未找到对应书籍书源")
            book.isLoading = true
            dao.updateBook(book)
            val bookDetails = script.getDetails(book.url)!!
            val chapterList = bookDetails.chapterList!!
            chapterList.forEachIndexed { index, chapter ->
                chapter.bookId = book.id
                chapter.index = index
            }
            book.chapterList = chapterList
            book.isLoading = false
            book.error = null
            //先检查章节内容是否有错
            val record = dao.getReadingRecord(book.title)

            val readingChapter = if (record != null) {
                ChapterMgr.getChapterByIndex(
                    record.bookId ?: "", record.chapterIndex
                )
            } else {
                null
            }
            val chapterLikeName = if (readingChapter != null) {
                ChapterMgr.getChapterLikeName(book.id, readingChapter.name())
                    .minByOrNull { abs(readingChapter.index - it.index) }
            } else {
                null
            }

            val content = dao.getChapterContent(book.id, chapterLikeName?.index ?: -1).firstOrNull()
            if (content?.contentError == true) {
                chapterList[content.chapterIndex].content = mutableListOf(content)
                ChapterMgr.getChapterContentByNet(chapterList[content.chapterIndex])
            }

            dao.updateBookDetails(bookDetails)
        } catch (e: Throwable) {
            Log.e("加载书籍详情：$book", e)
            book.isLoading = false
            book.error = android.util.Log.getStackTraceString(e)
            dao.updateBook(book)
        }
    }
}