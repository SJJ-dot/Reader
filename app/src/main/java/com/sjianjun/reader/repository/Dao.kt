package com.sjianjun.reader.repository

import androidx.room.*
import androidx.room.Dao
import com.sjianjun.reader.bean.*
import kotlinx.coroutines.flow.Flow

@Dao
interface Dao {
    @Query("SELECT * FROM JavaScript WHERE source = :source")
    fun getJavaScriptBySource(source: String): Flow<JavaScript>

    @Query("SELECT * FROM JavaScript")
    fun getAllJavaScript(): Flow<List<JavaScript>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJavaScript(javaScript: List<JavaScript>)

    @Query("select * from JavaScript where source in (select source from Book where title=:bookTitle and author=:bookAuthor)")
    fun getBookJavaScript(bookTitle: String, bookAuthor: String): Flow<List<JavaScript>>

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

    @Query("select * from Book where id in (select readingBookId from ReadingRecord)")
    fun getAllReadingBook(): Flow<List<Book>>

    @Query("select * from Book where id=:id")
    fun getBookById(id: Int): Flow<Book>

    @Query("select * from Book where title=:title and author=:author")
    fun getBookByTitleAndAuthor(title: String, author: String): Flow<List<Book>>

    @Query("select * from Book where url=:url")
    suspend fun getBookByUrl(url: String): Book

    @Query("select * from Chapter where bookId=:bookId order by id DESC limit 1")
    fun getLastChapterByBookId(bookId: Int): Flow<Chapter>

    /**
     * 查询列表不查章节内容
     */
    @Query("select id,bookId,title,url,isLoaded from Chapter where bookId=:bookId")
    fun getChapterListByBookId(bookId: Int): Flow<List<Chapter>>

    @Query("select * from Chapter where id=:id")
    fun getChapterById(id: Int): Flow<Chapter>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapterList: List<Chapter>): List<Long>

    @Query("delete from Chapter where bookId=:bookId")
    suspend fun deleteChapterByBookId(bookId: Int)

    @Query("select * from ReadingRecord where bookTitle=:bookTitle and bookAuthor=:bookAuthor")
    fun getReadingRecord(bookTitle: String, bookAuthor: String): Flow<ReadingRecord>

    @Query("select * from ReadingRecord")
    fun getAllReadingRecordList(): Flow<List<ReadingRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingRecord(record: ReadingRecord): Long

    @Query("delete from ReadingRecord where bookTitle=:bookTitle and bookAuthor=:bookAuthor")
    suspend fun deleteReadingRecord(bookTitle: String, bookAuthor: String)
}