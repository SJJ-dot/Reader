package com.sjianjun.reader.repository.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sjianjun.reader.bean.SearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    /**
     * 查询全部搜索历史记录
     */
    @Query("SELECT * FROM SearchHistory")
    fun getAllSearchHistory(): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSearchHistory(history: SearchHistory)

    @Delete
    fun deleteSearchHistory(historyList: List<SearchHistory>)

    @Query("DELETE FROM SearchHistory")
    fun deleteAllSearchHistory()

}