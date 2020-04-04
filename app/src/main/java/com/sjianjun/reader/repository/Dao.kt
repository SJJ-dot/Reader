package com.sjianjun.reader.repository

import androidx.room.*
import androidx.room.Dao
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.SearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface Dao {
    @Query("SELECT * FROM JavaScript WHERE source = :source")
    fun getJavaScriptBySource(source: String): Flow<JavaScript>

    @Query("SELECT * FROM JavaScript")
    fun getAllJavaScript(): Flow<List<JavaScript>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJavaScript(javaScript: List<JavaScript>)


    /**
     * 查询全部搜索历史记录
     */
    @Query("SELECT * FROM SearchHistory")
    fun getAllSearchHistory(): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(history: SearchHistory)

    @Delete
    suspend fun deleteSearchHistory(historyList: List<SearchHistory>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(bookList: List<Book>): List<Long>

    @Query("delete from Book where title=:title and author=:author ")
    suspend fun deleteBook(title: String, author: String)

    @Update
    suspend fun updateBook(book: Book)

    @Query("select * from Book")
    fun getAllBook(): Flow<List<Book>>

    @Query("select * from Book where id=:id")
    fun getBookById(id: Int): Flow<Book>

    @Query("select * from Book where title=:title and author=:author")
    fun getBookByTitleAndAuthor(title: String, author: String): Flow<List<Book>>

    /**
     * 查询列表不查章节内容
     */
    @Query("select id,bookId,title,url,isLoaded from Chapter where bookId=:bookId")
    fun getChapterListByBookId(bookId: Int): Flow<List<Chapter>>

    @Query("select * from Chapter where id=:id")
    fun getChapterById(id: Int): Flow<Chapter>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapter(chapterList: List<Chapter>): List<Long>

    @Query("delete from Chapter where bookId=:bookId")
    fun deleteChapterByBookId(bookId: Int)

}