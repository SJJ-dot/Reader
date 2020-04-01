package com.sjianjun.reader.repository

import androidx.lifecycle.LiveData
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.SearchHistory
import com.sjianjun.reader.rhino.js
import com.sjianjun.reader.utils.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sjj.alog.Log

object DataManager {
    private val dao = db.dao()


    /**
     * 搜素历史记录
     */
    fun getAllSearchHistory(): Flow<List<SearchHistory>> {
        return dao.getAllSearchHistory()
    }

    /**
     * 搜索书籍。搜索结果插入数据库。由数据库更新。
     */
    suspend fun search(query: String) {
        dao.insertSearchHistory(SearchHistory(query = query))
        dao.getAllJavaScript().flatMapMerge {
            it.asFlow()
        }.flatMapMerge {
            it.search(query)
        }.collect()
    }

    suspend fun deleteSearchHistory(history: List<SearchHistory>) {
        dao.deleteSearchHistory(history)
    }
}