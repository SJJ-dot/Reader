package com.sjianjun.reader.module.reader.activity

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import sjj.alog.Log
import sjj.novel.view.reader.bean.BookRecordBean
import sjj.novel.view.reader.page.TxtChapter

class BookReaderViewModel : ViewModel() {
    val book = MutableLiveData<Book?>()
    val chapterList = MutableLiveData<List<Chapter>>()
    val readingRecord = MutableLiveData<ReadingRecord>()

    private val bookDao get() = DbFactory.db.bookDao()
    private val chapterContentDao get() = DbFactory.db.chapterContentDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()
    private val chapterDao get() = DbFactory.db.chapterDao()

    suspend fun init(bookId: String): Book? = withIo {
        val book = bookDao.getBookById(bookId) ?: return@withIo null
        book.record = readingRecordDao.getReadingRecord(book.title).firstOrNull() ?: ReadingRecord(book.title)
        book.record?.bookId = book.id
        val chapterList = chapterDao.getChapterListByBookId(book.id).first()
        book.chapterList = chapterList
        withMain {
            this@BookReaderViewModel.book.value = book
            this@BookReaderViewModel.chapterList.value = chapterList
            this@BookReaderViewModel.readingRecord.value = book.record
        }
        return@withIo book
    }

    suspend fun maskChapterContentErr(chapter: Chapter, txtChapter: TxtChapter?) = withIo {
        if (chapter.content?.firstOrNull()?.contentError != false) {
            chapter.content?.firstOrNull()?.contentError = false
            txtChapter?.title = chapter.title
            toast("已取消标记章节内容错误")
        } else {
            chapter.content?.firstOrNull()?.contentError = true
            txtChapter?.title = chapter.title + "(章节内容错误)"
            toast("已标记章节内容错误")
        }
        chapter.content?.firstOrNull()?.let { chapterContentDao.insert(it) }
    }

    suspend fun reloadBookFromNet() {
        BookUseCase.reloadBookFromNet(this@BookReaderViewModel.book.value)
        this@BookReaderViewModel.chapterList.postValue(this@BookReaderViewModel.book.value?.chapterList ?: emptyList())
    }

    fun saveRecord(bean: BookRecordBean) {
        val record = readingRecord.value ?: return
        if (bean.chapter != record.chapterIndex ||
            bean.pagePos != record.offest ||
            bean.isEnd != record.isEnd
        ) {
            Log.i("保存阅读记录 $bean")
            viewModelScope.launch(Dispatchers.IO) {
                record.chapterIndex = bean.chapter
                record.offest = bean.pagePos
                record.isEnd = bean.isEnd
                record.updateTime = System.currentTimeMillis()
                readingRecordDao.insertReadingRecord(record)
            }
        }
    }

    suspend fun getChapterContentPage(chapter: Chapter): Boolean {
        return BookUseCase.getChapterContentPage(chapter)
    }

    /**
     * 加载 上一章 当前章 下一章
     */
    suspend fun getChapterContent(chapter: Chapter?, force: Int = 0) = withIo {
        chapter ?: return@withIo false

        while (chapter.isLoading.get()) {
            delay(100)
        }

        if (chapter.isLoaded && chapter.content != null && force != 1) {
            return@withIo false
        }

        if (!chapter.isLoading.compareAndSet(false, true)) {
            return@withIo false
        }

        try {
            //force
            BookUseCase.getChapterContent(chapter, force)
        } finally {
            chapter.isLoading.set(false)
        }

        return@withIo true
    }
}