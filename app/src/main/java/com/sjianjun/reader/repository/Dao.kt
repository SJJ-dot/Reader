package com.sjianjun.reader.repository

import androidx.room.*
import androidx.room.Dao
import com.sjianjun.reader.bean.*
import kotlinx.coroutines.flow.Flow

@Dao
interface Dao {
    @Query("SELECT * FROM JavaScript WHERE source = :source")
    fun getJavaScriptBySource(source: String): Flow<JavaScript?>

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

    @Transaction
    suspend fun insertBookAndSaveReadingRecord(bookList: List<Book>): String {
        insertBook(bookList)
        val book = bookList.first()
        val readingRecord = getReadingRecord(book.title, book.author)
        if (readingRecord == null) {
            insertReadingRecord(ReadingRecord().apply {
                bookTitle = book.title
                bookAuthor = book.author
                bookUrl = book.url
            })
            return book.url
        }
        return readingRecord.bookUrl
    }


    @Query("delete from Book where title=:title and author=:author ")
    suspend fun deleteBook(title: String, author: String)

    @Transaction
    suspend fun deleteBook(book: Book) {
        deleteChapterContentByBookUrl(book.title, book.author)
        deleteBook(book.title, book.author)
        deleteChapterByBook(book.title, book.author)
        deleteReadingRecord(book.title, book.author)
    }


    @Update
    suspend fun updateBook(book: Book)

    @Transaction
    suspend fun updateBookDetails(book: Book) {
        updateBook(book)
        val chapterList = book.chapterList ?: return
        deleteChapterByBookUrl(book.url)
        insertChapter(chapterList)
        updateBookChapterIsLoaded(book.url)
    }

    @Query("select * from Book")
    fun getAllBook(): Flow<List<Book>>

    @Query("select * from Book where url in (select bookUrl from ReadingRecord)")
    fun getAllReadingBook(): Flow<List<Book>>

    @Query("select * from Book where url=:url")
    fun getBookByUrl(url: String): Flow<Book?>

    @Query("select * from Book where title=:title and author=:author")
    fun getBookByTitleAndAuthor(title: String, author: String): Flow<List<Book>>

    @Query("select * from Chapter where bookUrl=:bookUrl order by `index` DESC limit 1")
    fun getLastChapterByBookUrl(bookUrl: String): Flow<Chapter?>

    /**
     * 查询列表不查章节内容
     */
    @Query("select * from Chapter where bookUrl=:bookUrl  order by `index`")
    fun getChapterListByBookUrl(bookUrl: String): Flow<List<Chapter>>

    @Query("select * from Chapter where url=:url")
    fun getChapterByUrl(url: String): Flow<Chapter?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapterList: List<Chapter>): List<Long>

    @Query("delete from Chapter where bookUrl=:bookUrl")
    suspend fun deleteChapterByBookUrl(bookUrl: String)

    @Query("delete from Chapter where url not in (:urlList)")
    suspend fun deleteChapterByUrl(urlList: List<String>)

    @Query("delete from Chapter where bookUrl in (select url from book where title=:bookTitle and author=:bookAuthor)")
    suspend fun deleteChapterByBook(bookTitle: String, bookAuthor: String)

    @Query("update Chapter set isLoaded=1 where bookUrl=:bookUrl and url in (select url from ChapterContent)")
    suspend fun updateBookChapterIsLoaded(bookUrl: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: Chapter, chapterContent: ChapterContent)

    @Query("select * from ChapterContent where url=:url")
    fun getChapterContent(url: String): Flow<ChapterContent?>

    @Query("delete from ChapterContent where bookUrl in (select url from Book where title=:bookTitle and author=:bookAuthor)")
    fun deleteChapterContentByBookUrl(bookTitle: String, bookAuthor: String)

    @Query("select * from ReadingRecord where bookTitle=:bookTitle and bookAuthor=:bookAuthor")
    fun getReadingRecordFlow(bookTitle: String, bookAuthor: String): Flow<ReadingRecord?>

    @Query("select * from ReadingRecord where bookTitle=:bookTitle and bookAuthor=:bookAuthor")
    suspend fun getReadingRecord(bookTitle: String, bookAuthor: String): ReadingRecord?

    @Query("select * from ReadingRecord")
    fun getAllReadingRecordList(): Flow<List<ReadingRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingRecord(record: ReadingRecord): Long

    @Query("delete from ReadingRecord where bookTitle=:bookTitle and bookAuthor=:bookAuthor")
    suspend fun deleteReadingRecord(bookTitle: String, bookAuthor: String)
}