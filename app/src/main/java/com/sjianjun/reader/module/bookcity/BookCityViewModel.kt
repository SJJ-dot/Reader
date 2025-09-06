package com.sjianjun.reader.module.bookcity

import androidx.lifecycle.ViewModel
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.repository.DbFactory

class BookCityViewModel : ViewModel() {
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()

    suspend fun getAllBookSourceSite(): List<String> = withIo {
        return@withIo bookSourceDao.getAllBookSource().sortedBy { it.name }.mapNotNull {
            if (it.enable) {
                val siteUrl = it.getSiteUrl()
                if (siteUrl.isNullOrBlank()) {
                    null
                } else {
                    siteUrl
                }
            } else {
                null
            }
        }

    }
}