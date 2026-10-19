package com.sjianjun.reader.tts.v2.load

import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.repository.ReplacementRuleUseCase
import com.sjianjun.reader.utils.ChapterTextPreparer
import com.sjianjun.reader.utils.format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import sjj.alog.Log

/**
 * 真实章节正文来源：数据库缓存优先，未入库时用书源脚本抓取（含分页拼接），
 * 再做与阅读页一致的文字处理（净化规则 + 简繁 + 全角）。
 *
 * 抓取部分复用现有的 [BookUseCase]（带去重与可取消），本类只做"取正文"这一件事。
 */
class BookChapterContentProvider : ChapterContentProvider {

    private companion object {
        const val TAG = "BookContentProvider"

        /** 单章最多拉取的分页数，避免异常数据把分页循环卡死 */
        const val MAX_PAGE_LOAD = 60
    }

    override suspend fun chapterCount(bookId: String): Int = withIo {
        runCatching { DbFactory.db.chapterDao().getChapterListByBookId(bookId).first().size }
            .getOrDefault(0)
    }

    override suspend fun load(bookId: String, chapterIndex: Int): ProvidedChapter? = withIo {
        val book = DbFactory.db.bookDao().getBookById(bookId) ?: run {
            Log.e("$TAG 未找到书籍：$bookId")
            return@withIo null
        }
        val chapters = DbFactory.db.chapterDao().getChapterListByBookId(bookId).first()
        val chapter = chapters.firstOrNull { it.index == chapterIndex } ?: run {
            Log.e("$TAG 未找到章节：$bookId #$chapterIndex")
            return@withIo null
        }
        try {
            BookUseCase.getChapterContent(chapter, 0)
            if (chapter.content?.firstOrNull()?.contentError == true) {
                Log.e("$TAG 章节内容错误：${chapter.title}")
                return@withIo null
            }
            var page = 0
            while (page < MAX_PAGE_LOAD && BookUseCase.getChapterContentPage(chapter)) {
                page++
            }
            val rawText = chapter.content
                ?.sortedBy { it.pageIndex }
                ?.joinToString(separator = "\n") { it.format() }
                .orEmpty()
            if (rawText.isBlank()) {
                Log.e("$TAG 章节正文为空：${chapter.title}")
                return@withIo null
            }
            val rules = ReplacementRuleUseCase.getEnabledRules()
            val jianFanMode = globalConfig.readerJianFanMode.value ?: ChapterTextPreparer.JIAN_FAN_OFF
            val fullWidthConvert = globalConfig.readerFullWidthConvert.value == true
            val title = ChapterTextPreparer.title(chapter.title, rules, book, chapter, jianFanMode, fullWidthConvert)
            val text = ChapterTextPreparer.content(rawText, rules, book, chapter, jianFanMode, fullWidthConvert)
            ProvidedChapter(title, text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e("$TAG 读取章节失败：${chapter.title}", e)
            null
        }
    }
}
