package com.sjianjun.reader.repository

import androidx.lifecycle.LiveData
import com.sjianjun.reader.bean.SearchHistory
import sjj.alog.Log

object DataManager {
    private val dao = db.dao()



    /**
     * 搜素历史记录
     */
    suspend fun getAllSearchHistory(): LiveData<List<SearchHistory>> {
        return dao.getAllSearchHistory()
    }
    /**
     * 搜索书籍。搜索结果插入数据库。由数据库更新。
     */
    suspend fun search(key: String) {
        Log.e("search:$key")
        dao.insertSearchHistory(SearchHistory(query = key))
    }

    suspend fun deleteSearchHistory(history: List<SearchHistory>) {
        dao.deleteSearchHistory(history)
    }
}