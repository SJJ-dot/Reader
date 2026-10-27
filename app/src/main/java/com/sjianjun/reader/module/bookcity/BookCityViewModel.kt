package com.sjianjun.reader.module.bookcity

import androidx.lifecycle.ViewModel
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.HomeUrl
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.repository.DbFactory.db
import kotlinx.coroutines.flow.Flow

class BookCityViewModel : ViewModel() {
    private val bookSourceDao get() = DbFactory.db.bookSourceDao()

    suspend fun getAllBookSourceSite(): List<HomeUrl> = withIo {
        return@withIo bookSourceDao.getAllBookSource().sortedBy { it.name }.mapNotNull {
            if (it.enable) {
                val siteUrl = it.getSiteUrl()
                if (siteUrl.isNullOrBlank()) {
                    null
                } else {
                    HomeUrl(url = siteUrl, title = it.name, supportSearch = it.hasFunction(BookSource.Func.search))
                }
            } else {
                null
            }
        }

    }

}