package com.sjianjun.reader.module.shelf

import androidx.lifecycle.ViewModel
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.repository.DbFactory.db
import kotlinx.coroutines.flow.Flow

class WebShelfViewModel : ViewModel() {
    private val dao = db.webBookDao()

    suspend fun getWebBook(): Flow<List<WebBook>> = withIo {
        dao.getWebBook()
    }

    suspend fun deleteWebBook(webBook: WebBook) = withIo {
        dao.deleteWebBookById(webBook.id)
    }

    suspend fun insertWebBook(webBook: WebBook) = withIo {
        dao.insertWebBook(webBook)
    }

    suspend fun getWebBookById(id: String): Flow<WebBook> = withIo {
        dao.getWebBookById(id)
    }
}