package com.sjianjun.reader.module.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.repository.DbFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class ChapterListViewModel : ViewModel() {
    val chapterListLiveData = MutableLiveData<List<Chapter>>()
    val readingRecord = MutableLiveData<ReadingRecord>()
    private val bookDao get() = DbFactory.db.bookDao()
    private val chapterDao get() = DbFactory.db.chapterDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()
    fun init(bookTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.getReadingBook(bookTitle).flatMapLatest {
                chapterDao.getChapterListByBookId(it?.id ?: "")
            }.collectLatest { chapterList ->
                chapterListLiveData.postValue(chapterList)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            readingRecordDao.getReadingRecord(bookTitle).collectLatest { record ->
                record?.let { readingRecord.postValue(it) }
            }
        }
    }

    suspend fun saveRecord(c: Chapter) = withIo {
        readingRecord.value?.let {
            it.chapterIndex = c.index
            it.offest = 0
            it.isEnd = false
            it.updateTime = System.currentTimeMillis()
            readingRecordDao.insertReadingRecord(it)
        }
    }
}