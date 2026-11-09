package com.sjianjun.reader.module.shelf

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory.db
import com.sjianjun.reader.utils.asFlow
import com.sjianjun.reader.utils.bookComparator
import com.sjianjun.reader.utils.debounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import sjj.alog.Log

class BookShelfViewModel : ViewModel() {
    val bookList = MutableLiveData<List<Book>>()
    private val bookDao get() = db.bookDao()
    private val recordDao get() = db.readingRecordDao()
    private val chapterDao get() = db.chapterDao()
    private val chapterContentDao get() = db.chapterContentDao()
    private val bookSourceDao get() = db.bookSourceDao()
    /**
     * @param forceFull true 时全量加载，以远端返回的完整章节列表重建本地章节；
     *        false 时只做增量检查更新
     */
    fun reloadBookFromNet(book: Book, forceFull: Boolean = false) = viewModelScope.launch {
        BookUseCase.reloadBookFromNet(book, forceFull = forceFull)
    }
    suspend fun reloadBookFromNet() = withIo {
        val sourceMap = mutableMapOf<String, MutableList<Book>>()
        bookList.value?.forEach {
            val list = sourceMap.getOrPut(it.bookSourceId) { mutableListOf() }
            list.add(it)
        }
        sourceMap.map { (_, books) ->
            async {
                val script = books.firstOrNull()?.bookSource ?: return@async
                val delay = script.requestDelay
                if (delay < 0) {
                    books.map {
                        async {
                            BookUseCase.reloadBookFromNet(it)
                        }
                    }.awaitAll()
                } else {
                    books.sortWith(bookComparator)
                    books.forEach {
                        BookUseCase.reloadBookFromNet(it)
                        delay(delay)
                    }
                }
            }
        }.awaitAll()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun init() {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.getAllReadingBook().flatMapLatest { books ->
                if (books.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // 一次性批量查询，替代对每本书串行发起 5~6 条 SQL。
                    // 书多时原先是 N×5 次数据库往返（N+1 瓶颈），这是启动列表变慢的主因。
                    val titles = books.map { it.title }

                    val lastChapterMap = chapterDao.getLastChapterByBookIds(books.map { it.id })
                        .associateBy { it.bookId }
                    val recordMap = recordDao.getReadingRecordsByTitles(titles)
                        .associateBy { it.bookTitle }
                    val readChapterMap = chapterDao.getReadChapterByTitles(titles)
                        .associate { row ->
                            val chapter = Chapter().apply {
                                bookId = row.bookId
                                url = row.url
                                title = row.title
                                isLoaded = row.isLoaded
                                index = row.index
                            }
                            row.recordTitle to chapter
                        }
                    val sourceCountMap = bookDao.getBookSourceCountByTitles(titles)
                        .associate { it.title to it.cnt }
                    val sourceMap = bookSourceDao.getBookSourcesByIds(books.map { it.bookSourceId })
                        .associateBy { it.id }

                    books.forEach { book ->
                        val record = recordMap[book.title]
                        val readChapter = readChapterMap[book.title]
                        book.lastChapter = lastChapterMap[book.id]
                        book.record = record
                        book.readChapter = readChapter
                        book.bookSourceCount = sourceCountMap[book.title] ?: 0
                        book.bookSource = sourceMap[book.bookSourceId]
                        book.unreadChapterCount =
                            (book.lastChapter?.index ?: 0) - (readChapter?.index ?: 0)
                        if (record?.isEnd != true) {
                            book.unreadChapterCount += 1
                        }
                    }

                    // 已读章节内容：仅对已加载的做内容观察，保留原有实时刷新语义；
                    // 同书名多书源会共享同一已读章节对象，按 (bookId,url) 去重避免重复加载。
                    val loaded = mutableSetOf<String>()
                    val contentFlows = books.mapNotNull { book ->
                        val rc = book.readChapter ?: return@mapNotNull null
                        if (!rc.isLoaded) return@mapNotNull null
                        val key = rc.bookId + "\u0000" + rc.url
                        if (!loaded.add(key)) return@mapNotNull null
                        chapterContentDao.getChapterContent(rc.bookId, rc.url).mapLatest { content ->
                            rc.content = content.toMutableList()
                            book
                        }
                    }
                    if (contentFlows.isEmpty()) flowOf(books) else combine(contentFlows) { books }
                }
            }.debounce(300).collectLatest { books ->
                bookList.postValue(books.sortedWith(bookComparator))
            }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            BookUseCase.delete(book)
        }
    }

    /**
     * 更新书籍排序值（保存到 ReadingRecord.order）
     */
    fun updateOrder(book: Book, order: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // 如果内存中已有 record，直接更新；否则尝试从数据库读取或新建记录
            book.record?.let {
                it.order = order
                recordDao.insertReadingRecord(it)
            }
        }
    }

}