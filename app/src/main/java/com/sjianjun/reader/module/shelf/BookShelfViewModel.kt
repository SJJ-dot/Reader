package com.sjianjun.reader.module.shelf

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

class BookShelfViewModel : ViewModel() {
    val bookList = MutableLiveData<List<Book>>()
    private val bookDao get() = db.bookDao()
    private val recordDao get() = db.readingRecordDao()
    private val chapterDao get() = db.chapterDao()
    private val chapterContentDao get() = db.chapterContentDao()
    private val bookSourceDao get() = db.bookSourceDao()
    fun reloadBookFromNet(book: Book) = viewModelScope.launch { BookUseCase.reloadBookFromNet(book) }
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

                combine(flows) {
                    it.toList()
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

}