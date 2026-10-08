package com.sjianjun.reader.module.shelf

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.Book
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
                val flows = books.map { book ->
                    val lastChapter = chapterDao.getLastChapterByBookId(book.id)
                    book.lastChapter = lastChapter
                    val record = recordDao.getReadingRecord(book.title).firstOrNull()
                    book.record = record
                    val readChapter = chapterDao.getChapterByIndex(record?.bookId ?: "", record?.chapterIndex ?: -1)
                    book.readChapter = readChapter
                    book.bookSourceCount = bookDao.getBookBookSourceNum(book.title)
                    book.bookSource = bookSourceDao.getBookSourceById(book.bookSourceId)
                    book.unreadChapterCount = (book.lastChapter?.index ?: 0) - (book.readChapter?.index ?: 0)
                    if (book.record?.isEnd != true) {
                        book.unreadChapterCount += 1
                    }
                    if (readChapter?.isLoaded == true) {
                        chapterContentDao.getChapterContent(readChapter.bookId, readChapter.index).mapLatest {
                            readChapter.content = it.toMutableList()
                            book
                        }
                    } else {
                        book.asFlow()
                    }
                }

                if (flows.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(flows) {
                        it.toList()
                    }
                }

            }.debounce(300).collectLatest { books ->
                bookList.postValue(books.sortedWith(bookComparator))
            }
        }
    }

    /**
     * 页面销毁时清理书架中书籍残留的加载中状态，
     * 避免加载被中断后 isLoading 一直为 true 导致书籍无法再次加载
     */
    fun resetBookLoadingState() {
        val ids = bookList.value?.filter { it.isLoading }?.map { it.id } ?: return
        if (ids.isEmpty()) return
        //用应用级作用域：本方法由 onDestroyView 调用，紧接着 ViewModel 可能就被销毁，
        //viewModelScope 一旦被取消，重置就不会执行
        App.app.appScope.launch(Dispatchers.IO) {
            runCatching { BookUseCase.resetBookLoadingState(ids) }
                .onFailure { Log.e("重置书籍加载状态失败", it) }
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