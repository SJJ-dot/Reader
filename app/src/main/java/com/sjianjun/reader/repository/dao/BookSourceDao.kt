package com.sjianjun.reader.repository.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sjianjun.reader.bean.BookSource
import kotlinx.coroutines.flow.Flow

@Dao
interface BookSourceDao {
    @Query("select * from BookSource where id in (select bookSourceId from Book where title=:bookTitle)")
    fun getBookBookSource(bookTitle: String): List<BookSource>

    @Query("select * from BookSource where id = (select bookSourceId from Book where id=:bookId)")
    fun getBookSourceByBookId(bookId: String): BookSource?

    @Query("select * from BookSource order by id")
    fun getAllBookSource(): Flow<List<BookSource>>

    @Query("select * from BookSource where enable!=0 order by id")
    fun getAllEnableBookSource(): Flow<List<BookSource>>

    @Query("select * from BookSource where id=:id")
    fun getBookSourceById(id: String): BookSource?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBookSource(source: List<BookSource>)

    @Delete
    fun deleteBookSource(source: List<BookSource>)
}