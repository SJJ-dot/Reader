package com.sjianjun.reader.repository.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sjianjun.reader.bean.BookSource

@Dao
interface BookSourceDao {

    @Query("select * from BookSource where id = (select bookSourceId from Book where id=:bookId)")
    fun getBookSourceByBookId(bookId: String): BookSource?

    @Query("select * from BookSource order by id")
    fun getAllBookSource(): List<BookSource>

    @Query("select * from BookSource where id=:id")
    fun getBookSourceById(id: String): BookSource?

    /**
     * 批量取多个书源。一次性 IN 查询，替代按书源 id 逐本查询。
     */
    @Query("select * from BookSource where id in (:ids)")
    fun getBookSourcesByIds(ids: List<String>): List<BookSource>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBookSource(source: List<BookSource>)

    @Delete
    fun deleteBookSource(source: List<BookSource>)
}