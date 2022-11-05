package com.sjianjun.reader.repository.dao

import androidx.room.*
import androidx.room.Dao
import com.sjianjun.reader.bean.*
import kotlinx.coroutines.flow.Flow
import sjj.alog.Log

@Dao
interface Dao {
    //<<<<<<<<<<<<<<<<<<<<<<<<<BookSource>>>>>>>>>>>>>>>>>>>>>>>>>
    @Query("select * from BookSource where id in (select bookSourceId from Book where title=:bookTitle and author=:bookAuthor)")
    fun getBookBookSource(bookTitle: String, bookAuthor: String): List<BookSource>

    @Query("select * from BookSource where id = (select bookSourceId from Book where id=:bookId)")
    fun getBookSourceByBookId(bookId: String): BookSource?

    @Query("select * from BookSource")
    fun getAllBookSource(): Flow<List<BookSource>>

    @Query("select * from BookSource where enable!=0")
    fun getAllEnableBookSource(): Flow<List<BookSource>>

    @Query("select * from BookSource where id=:id")
    fun getBookSourceById(id: String): Flow<BookSource?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBookSource(source: List<BookSource>)

    @Delete
    fun deleteBookSource(source: List<BookSource>)

    //<<<<<<<<<<<<<<<<<<<<<<<<<SearchHistory>>>>>>>>>>>>>>>>>>>>>>>>>
    /**
     * 查询全部搜索历史记录
     */
    @Query("SELECT * FROM SearchHistory")
    fun getAllSearchHistory(): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSearchHistory(history: SearchHistory)

    @Delete
    fun deleteSearchHistory(historyList: List<SearchHistory>)

    //<<<<<<<<<<<<<<<<<<<<<<<<<Book>>>>>>>>>>>>>>>>>>>>>>>>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBook(bookList: List<Book>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBook(book: Book): Long

    @Transaction
    fun saveSearchResult(bookList: List<Book>): String {
        cleanDirtyData()
        insertBook(bookList)
        val book = bookList.first()
        val readingRecord = getReadingRecord(book.title, book.author)
        if (bookList.find { it.id == readingRecord?.bookId } == null) {
            insertReadingRecord(ReadingRecord(book.title, book.author, book.id))
            return book.id
        }
        Log.i("保存搜索结果记录：$readingRecord $bookList")
        return readingRecord!!.bookId
    }


    @Query("delete from Book where title=:title and author=:author ")
    fun deleteBook(title: String, author: String): Int

    @Query("delete from Book where id=:id")
    fun deleteBookById(id: String)

    @Transaction
    fun deleteBook(book: Book) {
        val ccnum = deleteChapterContentByBookTitleAndAuthor(book.title, book.author)
        Log.i("删除内容数量：$ccnum")
        val bookNum = deleteBook(book.title, book.author)
        Log.i("删除书籍数量：$bookNum")
        val chapterNum = deleteChapterByBookTitleAndAuthor(book.title, book.author)
        Log.i("删除章节数量：$chapterNum")
        val rn = deleteReadingRecord(book.title, book.author)
        Log.i("删除阅读记录数量：$rn")
        cleanDirtyData()
    }

    /**
     * 删除指定书源的书籍
     */
    @Transaction
    fun deleteBookById(book: Book) {
        deleteChapterContentByBookId(book.id)
        deleteBookById(book.id)
        deleteChapterByBookId(book.id)
    }

    @Update
    fun updateBook(book: Book)

    @Transaction
    fun updateBookDetails(book: Book) {
        updateBook(book)
        deleteChapterByBookId(book.id)
        val chapterList = book.chapterList ?: return
        insertChapter(chapterList)
        updateBookChapterIsLoaded(book.id)
    }

    @Query("select * from Book")
    fun getAllBook(): Flow<List<Book>>

    @Query("select * from Book where id in (select bookId from ReadingRecord) order by title")
    fun getAllReadingBook(): Flow<List<Book>>

    @Query("select * from Book where id=:id")
    fun getBookById(id: String): Book?

    @Query("select count(*) from Book where title=:title and author=:author")
    fun getBookBookSourceNum(title: String, author: String): Int

    @Query("select * from Book where title=:title and author=:author  order by bookSourceId")
    fun getBookByTitleAndAuthor(title: String, author: String): Flow<List<Book>>

    @Query("select * from Book where title=:title and author=:author and bookSourceId=:bookSourceId")
    fun getBookByTitleAuthorAndSource(
        title: String,
        author: String,
        bookSourceId: String
    ): Flow<Book?>

    @Query("select * from Book where id in (select bookId from ReadingRecord where bookTitle=:title and bookAuthor=:author)")
    fun getReadingBook(title: String, author: String): Flow<Book?>


    //    <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<Chapter>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    @Query("select * from Chapter where bookId=:bookId order by `index` DESC limit 1")
    fun getLastChapterByBookId(bookId: String): Flow<Chapter?>

    /**
     * 查询列表不查章节内容
     */
    @Query("select * from Chapter where bookId=:bookId  order by `index`")
    fun getChapterListByBookId(bookId: String): Flow<List<Chapter>>

    @Query("select * from Chapter where title=:title and bookId=:bookId")
    fun getChapterByTitle(bookId: String, title: String): Flow<List<Chapter>>

    @Query("select * from Chapter where bookId=:bookId and title like :name")
    fun getChapterLikeName(bookId: String, name: String): Flow<List<Chapter>>

    @Query("select * from Chapter where bookId=:bookId and `index` =:index")
    fun getChapterByIndex(bookId: String, index: Int): Flow<Chapter?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapter(chapterList: List<Chapter>): List<Long>

    @Query("delete from Chapter where bookId=:bookId")
    fun deleteChapterByBookId(bookId: String)

    @Query("delete from Chapter where bookId in (select id from Book where title=:bookTitle and author=:bookAuthor)")
    fun deleteChapterByBookTitleAndAuthor(bookTitle: String, bookAuthor: String): Int

    @Query("update Chapter set isLoaded=1 where bookId=:bookId and (bookId+`index`) in (select (bookId+chapterIndex) from ChapterContent)")
    fun updateBookChapterIsLoaded(bookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapter(chapter: Chapter, chapterContent: ChapterContent)

    @Query("select * from ChapterContent where bookId=:bookId and chapterIndex=:chapterIndex")
    fun getChapterContent(bookId: String, chapterIndex: Int): Flow<ChapterContent?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapterContent(chapterContent: ChapterContent): Long

    @Query("delete from ChapterContent where bookId in (select id from Book where title=:bookTitle and author=:bookAuthor)")
    fun deleteChapterContentByBookTitleAndAuthor(bookTitle: String, bookAuthor: String): Int

    @Query("delete from ChapterContent where bookId=:bookId")
    fun deleteChapterContentByBookId(bookId: String)

    @Query("select * from ReadingRecord where bookTitle=:bookTitle and bookAuthor=:bookAuthor")
    fun getReadingRecordFlow(bookTitle: String, bookAuthor: String): Flow<ReadingRecord?>

    @Query("select * from ReadingRecord where bookTitle=:bookTitle and bookAuthor=:bookAuthor")
    fun getReadingRecord(bookTitle: String, bookAuthor: String): ReadingRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReadingRecord(record: ReadingRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReadingRecordList(record: List<ReadingRecord>): List<Long>

    @Query("select * from ReadingRecord")
    fun getAllReadingRecord(): List<ReadingRecord>

    @Query("delete from ReadingRecord where bookTitle=:bookTitle and bookAuthor=:bookAuthor")
    fun deleteReadingRecord(bookTitle: String, bookAuthor: String): Int

    @Transaction
    fun cleanDirtyData() {
        val readingRecord = cleanReadingBook()
        Log.i("清理脏数据 readingRecord $readingRecord")
        val book = cleanBook()
        Log.i("清理脏数据 book $book")
        val chapter = cleanChapter()
        Log.i("清理脏数据 chapter $chapter")
        val content = cleanChapterContent()
        Log.i("清理脏数据 content $content")

    }

    //有空再改连表查询吧
    @Query("delete from Book where id not in (select bookId from ReadingRecord)")
    fun cleanBook(): Int

    @Query("delete from ReadingRecord where bookId not in (select id from Book)")
    fun cleanReadingBook(): Int

    @Query("delete from Chapter where bookId not in (select id from Book)")
    fun cleanChapter(): Int

    @Query("delete from ChapterContent where bookId not in (select id from Book)")
    fun cleanChapterContent(): Int
}