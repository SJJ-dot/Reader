package com.sjianjun.reader.repository

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.Dao
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.SearchHistory

@Dao
interface Dao {
    @Query("SELECT * FROM JavaScript WHERE source = :source")
    suspend fun getJavaScriptBySource(source: String): LiveData<JavaScript>

    @Query("SELECT * FROM JavaScript")
    suspend fun getAllJavaScript(): LiveData<List<JavaScript>>

    /**
     * 查询全部搜索历史记录
     */
    @Query("SELECT * FROM SearchHistory")
    suspend fun getAllSearchHistory(): LiveData<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(history: SearchHistory)

    @Delete
    suspend fun deleteSearchHistory(historyList: List<SearchHistory>)
}