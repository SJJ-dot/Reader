package com.sjianjun.reader.module.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.utils.launchIo
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

class BookDetailsViewModel : ViewModel() {
    val bookLivedata = MutableLiveData<Book>()
    private val bookDao get() = DbFactory.db.bookDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()
    private val chapterDao get() = DbFactory.db.chapterDao()
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()
    @OptIn(FlowPreview::class)
    fun init(title: String) {
        launchIo {
            bookDao.getReadingBook(title).debounce(300).collectLatest {
                it?.also {
                    val record = readingRecordDao.getReadingRecord(it.title)
                    val chapter = chapterDao.getChapterByIndex(record?.bookId ?: "", record?.chapterIndex ?: -1)
                    it.readChapter = chapter
                    it.bookSourceCount = bookDao.getBookBookSourceNum(it.title)
                    it.bookSource = bookSourceDao.getBookSourceById(it.bookSourceId)
                    it.lastChapter = chapterDao.getLastChapterByBookId(it.id)
                }
                bookLivedata.value = it
            }
        }
    }

    fun reloadBookFromNet() {
        launchIo {
            BookUseCase.reloadBookFromNet(bookLivedata.value ?: return@launchIo)
        }
    }

    suspend fun setRecordToLastChapter() = withIo {
        val book = bookLivedata.value ?: return@withIo
        val lastChapter = book.lastChapter
        val readingRecord = readingRecordDao.getReadingRecord(book.title) ?: ReadingRecord(book.title, book.id)
        if (readingRecord.chapterIndex != lastChapter?.index) {
            readingRecord.chapterIndex = lastChapter?.index ?: 0
            readingRecord.offest = 0
            readingRecord.isEnd = false
            readingRecord.updateTime = System.currentTimeMillis()
            readingRecordDao.insertReadingRecord(readingRecord)
        }
    }
}