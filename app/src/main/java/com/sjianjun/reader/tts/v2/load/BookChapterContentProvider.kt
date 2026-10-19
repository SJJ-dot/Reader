package com.sjianjun.reader.tts.v2.load

import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.preferences.globalConfig
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
 * 整章抓取（首页 + 全部分页）交给 [NovelChapterLoader]，与阅读页共用同一套加载/重试/缓存策略，
 * 本类只做"取正文 + 文字处理"。
 */
class BookChapterContentProvider : ChapterContentProvider {

    private companion object {
        const val TAG = "BookContentProvider"
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
            // 整章加载（首页 + 全部分页）交给加载层的阅读页入口，重试由外层 NovelLoadService 负责
            if (!NovelChapterLoader.instance.loadWholeChapter(chapter, retries = 1)) {
                Log.e("$TAG 章节内容加载失败：${chapter.title}")
                return@withIo null
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
