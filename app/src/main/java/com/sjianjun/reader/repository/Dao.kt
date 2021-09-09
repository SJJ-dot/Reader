package com.sjianjun.reader.repository

import androidx.room.*
import androidx.room.Dao
import com.sjianjun.reader.bean.*
import kotlinx.coroutines.flow.Flow
import sjj.alog.Log

@Dao
interface Dao {


    @Query("select source from Book where title=:bookTitle and author=:bookAuthor")
    suspend fun getAllBookSource(bookTitle: String, bookAuthor: String): List<String>

    /**
     * 查询全部搜索历史记录
     */
    @Query("SELECT * FROM SearchHistory")
    fun getAllSearchHistory(): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(history: SearchHistory)

    @Delete
    suspend fun deleteSearchHistory(historyList: List<SearchHistory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(bookList: List<Book>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: Book): Long

    @Transaction
    suspend fun insertBookAndSaveReadingRecord(bookList: List<Book>): String {
        cleanDirtyData()
        insertBook(bookList)
        val book = bookList.first()
        val readingRecord = getReadingRecord(book.title, book.author)
        if (readingRecord?.bookUrl != book.url) {
            insertReadingRecord(ReadingRecord().apply {
                bookTitle = book.title
                bookAuthor = book.author
                bookUrl = book.url
            })
            return book.url
        }
        Log.i("保存搜索结果记录：$readingRecord $bookList")
        return readingRecord.bookUrl
    }


    @Query("delete from Book where title=:title and author=:author ")
    suspend fun deleteBook(title: String, author: String)

    @Query("delete from Book where url=:url")
    suspend fun deleteBookByUrl(url: String)

    @Transaction
    suspend fun deleteBook(book: Book) {
        deleteChapterContentByBookTitleAndAuthor(book.title, book.author)
        deleteBook(book.title, book.author)
        deleteChapterByBook(book.title, book.author)
        deleteReadingRecord(book.title, book.author)

        cleanDirtyData()
    }

    /**
     * 删除指定书源的书籍
     */
    @Transaction
    suspend fun deleteBookByUrl(book: Book) {
        deleteChapterContentByBookUrl(book.url)
        deleteBookByUrl(book.url)
        deleteChapterByBookUrl(book.url)
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

    @Query("select * from Book where url in (select bookUrl from ReadingRecord) order by title")
    fun getAllReadingBook(): Flow<List<Book>>

    @Query("select * from Book where url=:url")
    fun getBookByUrl(url: String): Flow<Book?>

    @Query("select * from Book where title=:title and author=:author  order by source")
    fun getBookByTitleAndAuthor(title: String, author: String): Flow<List<Book>>

    @Query("select * from Book where title=:title and author=:author and source=:source")
    fun getBookByTitleAuthorAndSource(title: String, author: String, source: String): Flow<Book?>

    @Query("select * from Book where url in (select bookUrl from ReadingRecord where bookTitle=:title and bookAuthor=:author)")
    fun getReadingBook(title: String, author: String): Flow<Book?>

    @Query("select * from Chapter where bookUrl=:bookUrl order by `index` DESC limit 1")
    fun getLastChapterByBookUrl(bookUrl: String): Flow<Chapter?>

    /**
     * 查询列表不查章节内容
     */
    @Query("select * from Chapter where bookUrl=:bookUrl  order by `index`")
    fun getChapterListByBookUrl(bookUrl: String): Flow<List<Chapter>>

    @Query("select * from Chapter where url=:url")
    fun getChapterByUrl(url: String): Flow<Chapter?>

    @Query("select * from Chapter where title=:title and bookUrl=:bookUrl")
    fun getChapterByTitle(bookUrl: String, title: String): Flow<List<Chapter>>

    @Query("select * from Chapter where bookUrl=:bookUrl and title like :name")
    fun getChapterByName(bookUrl: String, name: String): Flow<List<Chapter>>

    @Query("select * from Chapter where bookUrl=:bookUrl and `index` =:index")
    fun getChapterByIndex(bookUrl: String, index: Int): Flow<Chapter?>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapterContent(chapterContent: ChapterContent): Long

    @Query("delete from ChapterContent where bookUrl in (select url from Book where title=:bookTitle and author=:bookAuthor)")
    fun deleteChapterContentByBookTitleAndAuthor(bookTitle: String, bookAuthor: String)

    @Query("delete from ChapterContent where bookUrl=:bookUrl")
    fun deleteChapterContentByBookUrl(bookUrl: String)

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


    @Transaction
    suspend fun cleanDirtyData() {
        cleanReadingBook()
        cleanBook()
        cleanChapter()
        cleanChapterContent()
    }

    //有空再改连表查询吧
    @Query("delete from Book where not exists (select * from Book where url in (select bookUrl from ReadingRecord))")
    suspend fun cleanBook()

    @Query("delete from ReadingRecord where bookUrl not in (select url from Book)")
    suspend fun cleanReadingBook()

    @Query("delete from Chapter where bookUrl not in (select url from Book)")
    suspend fun cleanChapter()

    @Query("delete from ChapterContent where url not in (select url from Chapter)")
    suspend fun cleanChapterContent()

}