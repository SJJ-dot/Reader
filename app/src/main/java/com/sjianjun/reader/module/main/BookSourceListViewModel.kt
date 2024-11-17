package com.sjianjun.reader.module.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.*
import com.sjianjun.reader.utils.bookComparator
import com.sjianjun.reader.utils.debounce
import com.sjianjun.reader.utils.name
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 书籍书源列表页
 */
class BookSourceListViewModel(bookTitle: String) : ViewModel() {

    val bookList = MutableLiveData<List<Book>>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            BookMgr.getBookAllSourceFlow(bookTitle).debounce(100).collectLatest {
                val record = ReadingRecordMgr.getReadingRecord(bookTitle)
                val readingChapter = if (record != null) {
                    ChapterMgr.getChapterByIndex(
                        record?.bookId ?: "", record?.chapterIndex ?: -1
                    )
                } else {
                    null
                }

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
            val chapterLikeName = ChapterMgr.getChapterLikeName(book.id, readingChapter.name())
            book.readChapter = chapterLikeName.minByOrNull { abs(readingChapter.index - it.index) }
        }

        book.lastChapter = ChapterMgr.getLastChapterByBookId(book.id)
        book.bookSource = BookSourceMgr.getBookSourceById(book.bookSourceId).firstOrNull()
        if (book.readChapter != null) {
            ChapterMgr.getChapterContentLocal(book.readChapter!!)
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
            async { BookMgr.reloadBookFromNet(it) }
        }.awaitAll()
    }


}