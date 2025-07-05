package com.sjianjun.reader.repository.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sjianjun.reader.bean.WebBook
import kotlinx.coroutines.flow.Flow

@Dao
interface WebBookDao {
    @Query("SELECT * FROM WebBook ORDER BY updateTime DESC")
    fun getWebBook(): Flow<List<WebBook>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWebBook(webBook: WebBook)

    @Query("delete from WebBook where id = :id")
    fun deleteWebBookById(id: String)

    @Query("SELECT * FROM WebBook WHERE id = :id")
    fun getWebBookById(id: String): Flow<WebBook>
}