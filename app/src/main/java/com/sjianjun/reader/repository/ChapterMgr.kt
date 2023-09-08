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

    suspend fun getChapterContentLocal(chapter: Chapter): ChapterContent? = withIo {
        if (chapter.isLoaded) {
            val chapterContent = dao.getChapterContent(chapter.bookId, chapter.index)
            chapter.content = chapterContent
            return@withIo chapterContent
        }
        return@withIo null
    }

    suspend fun getChapterContentByNet(chapter: Chapter) = withIo {
        val js = dao.getBookSourceByBookId(chapter.bookId) ?: return@withIo
        val content = js.getChapterContent(chapter.url)
        if (content.isNullOrBlank()) {
            chapter.content = ChapterContent(chapter.bookId, chapter.index, "章节内容加载失败", true)
        } else {
            var error = false
            if (chapter.content?.contentError == true && chapter.content?.content == content) {
                error = true
            }
            chapter.content = ChapterContent(chapter.bookId, chapter.index, content, error)
        }
        chapter.isLoaded = true
        dao.insertChapter(chapter, chapter.content!!)
    }


    fun getChapterByIndex(bookId: String, index: Int): Chapter? {
        return dao.getChapterByIndex(bookId, index)
    }

    fun getChapterLikeName(bookId: String, chapterName: String): List<Chapter> {
        return dao.getChapterLikeName(bookId, "%${chapterName}%")
    }
}