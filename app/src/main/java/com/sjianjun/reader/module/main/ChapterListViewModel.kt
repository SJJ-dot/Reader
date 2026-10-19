package com.sjianjun.reader.module.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ChapterCacheState
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.repository.ReplacementRuleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ChapterListViewModel : ViewModel() {
    val chapterListLiveData = MutableLiveData<List<Chapter>>()
    val readingRecord = MutableLiveData<ReadingRecord>()

    /**
     * 当前书籍是否正在加载。下拉「重新加载」、上拉「检查更新」都会把书籍置为加载中，
     * 用它驱动刷新动画：请求可能长时间不返回，动画跟着加载状态才能保证该收的时候一定收得掉
     */
    val bookIsLoadingLiveData = MutableLiveData<Boolean>()
    private val bookDao get() = DbFactory.db.bookDao()
    private val chapterDao get() = DbFactory.db.chapterDao()
    private val chapterContentDao get() = DbFactory.db.chapterContentDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()
    private var bookJob: Job? = null
    private var readingRecordJob: Job? = null
    /**
     * @param bookId 指定查看某一书源的目录（换源内容对比里的「目录」）；为空则看当前正在阅读的书源
     */
    fun init(bookTitle: String, bookId: String? = null) {
        bookJob?.cancel()
        bookJob = viewModelScope.launch(Dispatchers.IO) {
            val bookFlow: Flow<Book?> = if (bookId.isNullOrBlank()) {
                bookDao.getReadingBook(bookTitle)
            } else {
                bookDao.observeBookById(bookId)
            }
            bookFlow.onEach {
                bookIsLoadingLiveData.postValue(it?.isLoading == true)
            }.flatMapLatest { book ->
                // 取启用净化规则，并对章节标题应用（作用域：书名 + 书URL + 章节标题）
                val rules = ReplacementRuleUseCase.getEnabledRules()
                val bookId = book?.id ?: ""
                chapterDao.getChapterListByBookId(bookId).map { chapterList ->
                    applyCacheState(applyTitleRules(chapterList, book, rules), bookId)
                }
            }.collectLatest { chapterList ->
                chapterListLiveData.postValue(chapterList)
            }
        }
        readingRecordJob?.cancel()
        readingRecordJob = viewModelScope.launch(Dispatchers.IO) {
            readingRecordDao.getReadingRecord(bookTitle).collectLatest { record ->
                record?.let { readingRecord.postValue(it) }
            }
        }
    }

    /**
     * 目录缓存标记以 `ChapterContent` 的实际内容为准（不再用 `Chapter.isLoaded`）：
     * - 有可用内容 → 已缓存（绿）
     * - 只有失败内容 → 缓存失败（红）
     * - 没有内容 → 未缓存（灰）
     *
     * 之前失败也会写一条内容行并把 isLoaded 置 true，导致"缓存失败"的章节显示成已缓存。
     */
    private suspend fun applyCacheState(chapterList: List<Chapter>, bookId: String): List<Chapter> {
        if (bookId.isBlank() || chapterList.isEmpty()) return chapterList
        val statusMap = runCatching { chapterContentDao.getChapterCacheStatus(bookId) }
            .getOrDefault(emptyList())
            .associateBy { it.chapterUrl }
        chapterList.forEach { chapter ->
            val status = statusMap[chapter.url]
            chapter.cacheState = ChapterCacheState.of(
                validCount = status?.validCount ?: 0,
                errorCount = status?.errorCount ?: 0,
            )
        }
        return chapterList
    }

    private suspend fun applyTitleRules(
        chapterList: List<Chapter>,
        book: Book?,
        rules: List<com.sjianjun.reader.bean.ReplacementRule>,
    ): List<Chapter> {
        if (rules.isEmpty()) return chapterList
        val b = book
        return chapterList.map { ch ->
            val raw = ch.title ?: return@map ch
            val cleaned = ReplacementRuleUseCase.applyRules(raw, rules, b, ch, applyToTitle = true)
            if (cleaned == raw) ch else ch.apply { title = cleaned }
        }
    }

    /**
     * 强制全量重新加载章节列表（不把本地最新章节链接传给脚本），用于本地章节数据异常时重建
     */
    suspend fun reloadBookFromNet(bookTitle: String, bookId: String? = null) = withIo {
        val book = findBook(bookTitle, bookId) ?: return@withIo
        BookUseCase.reloadBookFromNet(book, forceFull = true)
    }

    /**
     * 检查更新：只拉取最新章节与本地已有章节拼接（增量）
     */
    suspend fun checkUpdate(bookTitle: String, bookId: String? = null) = withIo {
        val book = findBook(bookTitle, bookId) ?: return@withIo
        BookUseCase.reloadBookFromNet(book, forceFull = false)
    }

    private suspend fun findBook(bookTitle: String, bookId: String?): Book? =
        if (bookId.isNullOrBlank()) bookDao.getReadingBook(bookTitle).firstOrNull()
        else bookDao.getBookById(bookId)

    suspend fun saveRecord(c: Chapter) = withIo {
        readingRecord.value?.let {
            it.chapterIndex = c.index
            it.offest = 0
            it.scrollOffset = 0
            it.isEnd = false
            it.updateTime = System.currentTimeMillis()
            readingRecordDao.insertReadingRecord(it)
        }
    }
}
