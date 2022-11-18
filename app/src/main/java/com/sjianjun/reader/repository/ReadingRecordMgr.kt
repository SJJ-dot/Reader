package com.sjianjun.reader.repository

import com.sjianjun.coroutine.withIo

object ReadingRecordMgr {
    private val dao = DbFactory.db.dao()

    suspend fun getReadingRecord(bookTitle: String, bookAuthor: String) = withIo{
       return@withIo dao.getReadingRecord(bookTitle, bookAuthor)
    }
}