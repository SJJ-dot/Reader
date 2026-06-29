package com.sjianjun.reader.module.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.repository.ReplacementRuleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ChapterListViewModel : ViewModel() {
    val chapterListLiveData = MutableLiveData<List<Chapter>>()
    val readingRecord = MutableLiveData<ReadingRecord>()
    private val bookDao get() = DbFactory.db.bookDao()
    private val chapterDao get() = DbFactory.db.chapterDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()
    private var bookJob: Job? = null
    private var readingRecordJob: Job? = null
    fun init(bookTitle: String) {
        bookJob?.cancel()
        bookJob = viewModelScope.launch(Dispatchers.IO) {
            bookDao.getReadingBook(bookTitle).flatMapLatest { book ->
                // 取启用净化规则，并对章节标题应用（作用域：书名 + 书URL + 章节标题）
                val rules = ReplacementRuleUseCase.getEnabledRules()
                chapterDao.getChapterListByBookId(book?.id ?: "").map { chapterList ->
                    applyTitleRules(chapterList, book, rules)
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
