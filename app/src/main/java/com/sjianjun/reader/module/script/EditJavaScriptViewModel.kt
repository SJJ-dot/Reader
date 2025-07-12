package com.sjianjun.reader.module.script

import androidx.lifecycle.ViewModel
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.repository.DbFactory

class EditJavaScriptViewModel:ViewModel() {
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()
    suspend fun saveJs(source: BookSource) = withIo {
        bookSourceDao.insertBookSource(listOf(source))
    }

    suspend fun getBookSource(sourceId: String): BookSource?  = withIo{
        bookSourceDao.getBookSourceById(sourceId)
    }
}