package com.sjianjun.reader.repository

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.Dao
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.SearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface Dao {
    @Query("SELECT * FROM JavaScript WHERE source = :source")
    fun getJavaScriptBySource(source: String): Flow<JavaScript>

    @Query("SELECT * FROM JavaScript")
    fun getAllJavaScript(): Flow<List<JavaScript>>

    /**
     * 查询全部搜索历史记录
     */
    @Query("SELECT * FROM SearchHistory")
    fun getAllSearchHistory(): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(history: SearchHistory)

    @Delete
    suspend fun deleteSearchHistory(historyList: List<SearchHistory>)
}