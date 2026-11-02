package com.sjianjun.reader.module.reader.activity

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.tts.v2.load.NovelChapterLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import sjj.novel.view.reader.bean.BookRecordBean
import sjj.novel.view.reader.page.TxtChapter

class BookReaderViewModel : ViewModel() {
    val book = MutableLiveData<Book?>()
    val chapterList = MutableLiveData<List<Chapter>>()
    val chapterCache = MutableLiveData<Boolean>()
    val contentError = MutableLiveData<Boolean>()

    private val bookDao get() = DbFactory.db.bookDao()
    private val chapterContentDao get() = DbFactory.db.chapterContentDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()
    private val chapterDao get() = DbFactory.db.chapterDao()
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()

    /** 章节列表跟随 DB 的收集任务：重新 init（换书/换源）时取消上一个，避免叠加多个收集器 */
    private var chapterListCollectJob: Job? = null

    suspend fun init(bookId: String): Book? = withIo {
        val book = bookDao.getBookById(bookId) ?: return@withIo null
        book.record = readingRecordDao.getReadingRecord(book.title).firstOrNull() ?: ReadingRecord(book.title)
        book.record?.bookId = book.id
        val chapterList = chapterDao.getChapterListByBookId(book.id).first()
        book.chapterList = chapterList
        book.bookSource = bookSourceDao.getBookSourceById(book.bookSourceId)
        book.bookSourceCount = bookDao.getBookBookSourceNum(book.title)
        withMain {
            this@BookReaderViewModel.book.value = book
            this@BookReaderViewModel.chapterList.value = chapterList
        }
        // 阅读页章节列表持续跟随 DB：增量更新/抽屉编辑后，内存不再停留于进页时的陈旧快照
        // （修复“应显示 97.5 却一直显示 98”——阅读页按陈旧 index→url 映射取章）
        chapterListCollectJob?.cancel()
        chapterListCollectJob = viewModelScope.launch(Dispatchers.IO) {
            chapterDao.getChapterListByBookId(book.id).collect { list ->
                withMain {
                    this@BookReaderViewModel.chapterList.value = list
                    this@BookReaderViewModel.book.value?.chapterList = list
                }
            }
        }
        return@withIo book
    }

    suspend fun maskChapterContentErr(chapter: Chapter, txtChapter: TxtChapter?) = withIo {
        if (chapter.content?.firstOrNull()?.contentError != false) {
            chapter.content?.firstOrNull()?.contentError = false
            txtChapter?.title = chapter.title
        } else {
            chapter.content?.firstOrNull()?.contentError = true
            txtChapter?.title = chapter.title + "(章节内容错误)"
        }
        chapter.content?.firstOrNull()?.let {
            //内容以章节链接为标识，写入前补齐，避免存成无法定位的脏数据
            it.chapterUrl = chapter.url
            chapterContentDao.insert(it)
        }
        contentError.postValue(true)
    }

    suspend fun reloadBookFromNet() {
        BookUseCase.reloadBookFromNet(this@BookReaderViewModel.book.value)
        this@BookReaderViewModel.chapterList.postValue(this@BookReaderViewModel.book.value?.chapterList ?: emptyList())
    }

    fun saveRecord(bean: BookRecordBean) {
        val record = book.value?.record ?: return
        if (bean.chapter != record.chapterIndex ||
            bean.pagePos != record.offest ||
            bean.isEnd != record.isEnd ||
            bean.scrollOffset != record.scrollOffset
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                record.chapterIndex = bean.chapter
                record.offest = bean.pagePos
                record.isEnd = bean.isEnd
                record.scrollOffset = bean.scrollOffset
                record.updateTime = System.currentTimeMillis()
                readingRecordDao.insertReadingRecord(record)
            }
        }
    }

    /**
     * 加载 上一章 当前章 下一章（加载层负责去重/重试）
     *
     * @return 是否拿到可用内容
     */
    suspend fun getChapterContent(chapter: Chapter?, force: Int = 0): Boolean {
        chapter ?: return false
        return NovelChapterLoader.instance.loadChapter(chapter, force)
    }
}