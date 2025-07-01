package com.sjianjun.reader.repository

import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ChapterContent
import kotlinx.coroutines.flow.firstOrNull

object ChapterMgr {
    private val dao = DbFactory.db.dao()
    suspend fun getLastChapterByBookId(bookId: String) = withIo {
        return@withIo dao.getLastChapterByBookId(bookId).firstOrNull()
    }

    suspend fun getChapterContentLocal(chapter: Chapter): List<ChapterContent>? = withIo {
        if (chapter.isLoaded) {
            val chapterContent = dao.getChapterContent(chapter.bookId, chapter.index)
            chapter.content = chapterContent.toMutableList()
            return@withIo chapterContent
        }
        return@withIo null
    }

    suspend fun getChapterContentByNet(chapter: Chapter) = withIo {
        val js = dao.getBookSourceByBookId(chapter.bookId) ?: return@withIo
        val content = js.getChapterContent(chapter.url)
        content.bookId = chapter.bookId
        content.chapterIndex = chapter.index
        if (!content.contentError){
            if (chapter.content?.firstOrNull()?.contentError == true && chapter.content?.firstOrNull()?.content == content.content) {
                content.contentError = true
            }
        }
        chapter.content = mutableListOf(content)
        chapter.isLoaded = true
        dao.insertChapter(chapter, content)
    }


    fun getChapterByIndex(bookId: String, index: Int): Chapter? {
        return dao.getChapterByIndex(bookId, index)
    }

    fun getChapterLikeName(bookId: String, chapterName: String): List<Chapter> {
        return dao.getChapterLikeName(bookId, "%${chapterName}%")
    }
}