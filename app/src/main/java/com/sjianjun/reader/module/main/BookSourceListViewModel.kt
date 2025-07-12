package com.sjianjun.reader.module.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.utils.bookComparator
import com.sjianjun.reader.utils.debounce
import com.sjianjun.reader.utils.name
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 书籍书源列表页
 */
class BookSourceListViewModel() : ViewModel() {

    val bookList = MutableLiveData<List<Book>>()
    private val bookDao get() = DbFactory.db.bookDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()
    private val chapterDao get() = DbFactory.db.chapterDao()
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()
    private val chapterContentDao get() = DbFactory.db.chapterContentDao()

    fun init(bookTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.getAllSourceBooksByTitle(bookTitle).debounce(100).collectLatest {
                val record = readingRecordDao.getReadingRecord(bookTitle).firstOrNull()
                val readingChapter = if (record != null) chapterDao.getChapterByIndex(record.bookId, record.chapterIndex) else null
                val list = it.map { async { loadBookInfo(it, record, readingChapter) } }.awaitAll()
                bookList.postValue(list.sortedWith(bookComparator))
            }
        }
    }

    private suspend fun loadBookInfo(
        book: Book,
        record: ReadingRecord?,
        readingChapter: Chapter?
    ): Book {

        if (readingChapter != null) {
            val chapterLikeName = chapterDao.getChapterLikeName(book.id, "%${readingChapter.name()}%")
            book.readChapter = chapterLikeName.minByOrNull { abs(readingChapter.index - it.index) }
        }

        book.lastChapter = chapterDao.getLastChapterByBookId(book.id)
        book.bookSource = bookSourceDao.getBookSourceById(book.bookSourceId)
        if (book.readChapter != null) {
            if (book.readChapter?.isLoaded == true) {
                val chapterContent = chapterContentDao.getChapterContent(book.readChapter!!.bookId, book.readChapter!!.index)
                book.readChapter!!.content = chapterContent.toMutableList()
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
        return book
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
}