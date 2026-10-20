package com.sjianjun.reader.module.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.ChapterLocator
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.repository.ReplacementRuleUseCase
import com.sjianjun.reader.utils.ChapterTextPreparer
import com.sjianjun.reader.utils.bookComparator
import com.sjianjun.reader.utils.debounce
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * 书籍书源列表页（换源）
 */
class BookSourceListViewModel() : ViewModel() {

    val bookList = MutableLiveData<List<Book>>()
    private val bookDao get() = DbFactory.db.bookDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()
    private val chapterDao get() = DbFactory.db.chapterDao()
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()
    private val chapterContentDao get() = DbFactory.db.chapterContentDao()

    /** 当前正在阅读的章节（旧书源），用于换源定位与内容对比 */
    private var currentChapter: Chapter? = null

    /** 当前阅读章节标题（旧书源），内容对比页据此判断定位到的章节是否同名 */
    val currentChapterTitle: String? get() = currentChapter?.title

    /** 当前阅读页首行文本：换源对比时作为定位锚点 */
    private var compareAnchor: String = ""

    fun init(bookTitle: String, chapterIndex: Int = -1, pageFirstLine: String = "") {
        compareAnchor = pageFirstLine.trim()
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.getAllSourceBooksByTitle(bookTitle).debounce(100).collectLatest {
                val record = readingRecordDao.getReadingRecord(bookTitle).firstOrNull()
                // 优先用阅读页实际所在章节（页码进度比阅读记录更准），没有再用阅读记录
                val readingChapter = if (record != null) {
                    (if (chapterIndex >= 0) chapterDao.getChapterByIndex(record.bookId, chapterIndex) else null)
                        ?: chapterDao.getChapterByIndex(record.bookId, record.chapterIndex)
                } else null
                currentChapter = readingChapter
                val list = it.map { async { loadBookInfo(it, record, readingChapter) } }.awaitAll()
                bookList.postValue(sortCurrentFirst(list, record))
            }
        }
    }

    /** 当前阅读的书源排第一，其余按原排序 */
    private fun sortCurrentFirst(list: List<Book>, record: ReadingRecord?): List<Book> {
        if (record == null) return list.sortedWith(bookComparator)
        return list.sortedWith { a, b ->
            val aCur = a.id == record.bookId
            val bCur = b.id == record.bookId
            if (aCur != bCur) return@sortedWith if (aCur) -1 else 1
            bookComparator.compare(a, b)
        }
    }

    private suspend fun loadBookInfo(
        book: Book,
        record: ReadingRecord?,
        readingChapter: Chapter?
    ): Book {

        if (readingChapter != null) {
            book.readChapter = ChapterLocator.locateChapter(
                newBookId = book.id,
                oldBookId = record?.bookId ?: readingChapter.bookId,
                oldIndex = readingChapter.index,
                oldTitle = readingChapter.title
            )
        }

        book.lastChapter = chapterDao.getLastChapterByBookId(book.id)
        if (book.readChapter == null) {
            book.readChapter = book.lastChapter
        }
        book.bookSource = bookSourceDao.getBookSourceById(book.bookSourceId)
        if (book.readChapter != null) {
            if (book.readChapter?.isLoaded == true) {
                val chapterContent = chapterContentDao.getChapterContent(book.readChapter!!.bookId, book.readChapter!!.url)
                book.readChapter!!.content = chapterContent.first().toMutableList()
            }

            val lastChapterIndex = book.lastChapter?.index ?: 0
            val readChapterIndex = book.readChapter?.index ?: 0
            book.unreadChapterCount = if (record?.isEnd == true) {
                lastChapterIndex - readChapterIndex
            } else {
                lastChapterIndex - readChapterIndex + 1
            }
        } else {
            book.unreadChapterCount = 0
        }
        book.record = record
        return book
    }

    /**
     * 内容对比模式：确保该书源有目录、定位到当前阅读章节、加载正文。
     * 没有目录时直接加载书籍，再定位。
     *
     * 结果按书缓存：bookList 每次 DB 变化都会重新发射，若不缓存会反复触发加载导致页面闪烁。
     * 刷新按钮走 force=true 强制重载。
     */
    private val preparedCompare = java.util.concurrent.ConcurrentHashMap<String, Book>()

    suspend fun prepareForCompare(book: Book, force: Boolean = false): Book = withIo {
        if (force) {
            preparedCompare.remove(book.id)
        } else {
            preparedCompare[book.id]?.let { return@withIo it }
        }
        val chapters = chapterDao.getChapterListByBookId(book.id).first()
        if (chapters.isEmpty()) {
            // 没有章节列表，直接加载书籍
            runCatching { BookUseCase.reloadBookFromNet(book) }
        }
        val record = readingRecordDao.getReadingRecord(book.title).firstOrNull()
        val old = currentChapter
            ?: record?.let { chapterDao.getChapterByIndex(it.bookId, it.chapterIndex) }
        val located = if (old != null) {
            ChapterLocator.locateChapter(
                newBookId = book.id,
                oldBookId = record?.bookId ?: old.bookId,
                oldIndex = old.index,
                oldTitle = old.title
            )
        } else {
            null
        }
        book.readChapter = located ?: chapterDao.getLastChapterByBookId(book.id)
        book.lastChapter = chapterDao.getLastChapterByBookId(book.id)
        book.bookSource = bookSourceDao.getBookSourceById(book.bookSourceId)
        book.record = record
        book.readChapter?.let { chapter ->
            BookUseCase.getChapterContent(chapter)
            // 拼接分页正文（与阅读页/朗读一致）
            var page = 0
            while (page < MAX_COMPARE_PAGE_LOAD && BookUseCase.getChapterContentPage(chapter)) {
                page++
            }
        }
        preparedCompare[book.id] = book
        book
    }

    /**
     * 与阅读页/朗读一致的文本处理：HTML 转纯文本 + 全角规范化 + 简繁转换 + 净化规则
     * @return (章节标题, 正文纯文本)
     */
    suspend fun formatCompareContent(book: Book, chapter: Chapter): Pair<String, String> = withIo {
        val rawText = chapter.content
            ?.sortedBy { it.pageIndex }
            ?.joinToString("\n") { it.format() }
            .orEmpty()
        val rules = ReplacementRuleUseCase.getEnabledRules()
        val jianFanMode = globalConfig.readerJianFanMode.value ?: ChapterTextPreparer.JIAN_FAN_OFF
        val fullWidthConvert = globalConfig.readerFullWidthConvert.value == true
        val title = ChapterTextPreparer.title(chapter.title, rules, book, chapter, jianFanMode, fullWidthConvert)
        val text = ChapterTextPreparer.content(rawText, rules, book, chapter, jianFanMode, fullWidthConvert)
        // 与阅读页一致：跳过空白行（净化规则删掉广告后会留下空行，阅读页排版时本来就会跳过）
        val cleaned = text.lines().filter { it.isNotBlank() }.joinToString("\n")
        title to cleaned
    }

    /** 对比页定位结果 */
    data class CompareLocation(
        /** 命中位置（字符偏移），-1 表示未命中 */
        val offset: Int,
        /** 命中位置占整章正文的百分比（0~100），-1 表示未命中 */
        val percent: Int,
        /** 实际用于匹配的文本片段 */
        val anchor: String,
        val hit: Boolean,
    )

    /**
     * 在新书源正文里按「当前阅读页第一行」定位。
     *
     * 分页折行、净化规则、标点差异可能让首行不完全一致，所以从长到短试几次前缀；
     * 都找不到则返回未命中（对比页按章节开头展示）。
     */
    fun locateInContent(text: String, anchor: String = compareAnchor): CompareLocation {
        val base = anchor.trim()
        if (base.isBlank() || text.isBlank()) return CompareLocation(-1, -1, base.take(12), false)
        val lengths = listOf(base.length, 12, 8, 6).distinct().filter { it > 0 }
        for (len in lengths) {
            val probe = base.take(minOf(len, base.length)).trim()
            if (probe.length < 6) continue
            val index = text.indexOf(probe)
            if (index >= 0) {
                return CompareLocation(index, index * 100 / text.length, probe, true)
            }
        }
        return CompareLocation(-1, -1, base.take(12), false)
    }

    suspend fun reloadAllBookFromNet() = withIo {
        val list = bookList.value ?: return@withIo
        list.map {
            async { BookUseCase.reloadBookFromNet(it) }
        }.awaitAll()
    }

    suspend fun changeReadingRecordBookSource(book: Book) = withIo {
        try {
            BookUseCase.changeReadingRecordBookSource(book)
        } catch (e: Exception) {
            toast("书源切换失败：${e.message}")
        }
    }

    suspend fun deleteBookById(book: Book) = withIo {
        try {
            BookUseCase.deleteById(book)
            return@withIo true
        } catch (e: Exception) {
            return@withIo false
        }
    }

    /** 批量删除书源 */
    suspend fun deleteBooks(books: List<Book>) = withIo {
        books.forEach { runCatching { BookUseCase.deleteById(it) } }
    }

    companion object {
        /** 内容对比最多拼接的分页数 */
        private const val MAX_COMPARE_PAGE_LOAD = 20
    }
}
