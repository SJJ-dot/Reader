package com.sjianjun.reader.repository

import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ChapterContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

object ChapterMgr {
    private val dao = DbFactory.db.dao()
    suspend fun getLastChapterByBookId(bookId: String) = withIo {
        return@withIo dao.getLastChapterByBookId(bookId).firstOrNull()
    }

    suspend fun getChapterContentLocal(chapter: Chapter): ChapterContent? {
        if (chapter.isLoaded) {
            val chapterContent = dao.getChapterContent(chapter.bookId, chapter.index)
            chapter.content = chapterContent
            return chapterContent
        }
        return null
    }

    fun getChapterByIndex(bookId: String, index: Int): Chapter? {
        return dao.getChapterByIndex(bookId, index)
    }

    fun getChapterLikeName(bookId: String, chapterName: String): List<Chapter> {
        return dao.getChapterLikeName(bookId, "%${chapterName}%")
    }
}