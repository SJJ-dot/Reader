package com.sjianjun.reader.repository.dao

import androidx.room.*
import androidx.room.Dao
import com.sjianjun.reader.bean.*
import kotlinx.coroutines.flow.Flow
import sjj.alog.Log

@Dao
interface Dao {
    //<<<<<<<<<<<<<<<<<<<<<<<<<BookSource>>>>>>>>>>>>>>>>>>>>>>>>>
    @Query("select * from BookSource where id in (select bookSourceId from Book where title=:bookTitle)")
    fun getBookBookSource(bookTitle: String): List<BookSource>

    @Query("select * from BookSource where id = (select bookSourceId from Book where id=:bookId)")
    fun getBookSourceByBookId(bookId: String): BookSource?

    @Query("select * from BookSource order by id")
    fun getAllBookSource(): Flow<List<BookSource>>

    @Query("select * from BookSource where enable!=0 order by id")
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
        val readingRecord = getReadingRecord(book.title)
        if (bookList.find { it.id == readingRecord?.bookId } == null) {
            insertReadingRecord(ReadingRecord(book.title, book.id))
            return book.id
        }
        Log.i("保存搜索结果记录：$readingRecord $bookList")
        return readingRecord!!.bookId
    }


    @Query("delete from Book where title=:title")
    fun deleteBook(title: String): Int

    @Query("delete from Book where id=:id")
    fun deleteBookById(id: String)

    @Transaction
    fun deleteBook(book: Book) {
        val ccnum = deleteChapterContentByBookTitleAndAuthor(book.title)
        Log.i("删除内容数量：$ccnum")
        val bookNum = deleteBook(book.title)
        Log.i("删除书籍数量：$bookNum")
        val chapterNum = deleteChapterByBookTitleAndAuthor(book.title)
        Log.i("删除章节数量：$chapterNum")
        val rn = deleteReadingRecord(book.title)
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

    @Query("select * from Book order by id")
    fun getAllBook(): Flow<List<Book>>

    @Query("select * from Book where id in (select bookId from ReadingRecord) order by id")
    fun getAllReadingBook(): Flow<List<Book>>

    @Query("select * from Book where id=:id")
    fun getBookById(id: String): Book?

    @Query("select count(*) from Book where title=:title")
    fun getBookBookSourceNum(title: String): Int

    @Query("select * from Book where title=:title order by bookSourceId")
    fun getBookByTitleAndAuthor(title: String): Flow<List<Book>>

    @Query("select * from Book where title=:title order by bookSourceId")
    fun getBookAllSourceFlow(title: String): Flow<List<Book>>

    @Query("select * from Book where id in (select bookId from ReadingRecord where bookTitle=:title)")
    fun getReadingBook(title: String): Flow<Book?>


    //    <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<Chapter>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    @Query("select * from Chapter where bookId=:bookId order by `index` DESC limit 1")
    fun getLastChapterByBookId(bookId: String): Flow<Chapter?>

    /**
     * 查询列表不查章节内容
     */
    @Query("select * from Chapter where bookId=:bookId  order by `index`")
    fun getChapterListByBookId(bookId: String): Flow<List<Chapter>>

    @Query("select * from Chapter where title=:title and bookId=:bookId order by `index`")
    fun getChapterByTitle(bookId: String, title: String): Flow<List<Chapter>>

    @Query("select * from Chapter where bookId=:bookId and title like :name")
    fun getChapterLikeName(bookId: String, name: String): List<Chapter>

    @Query("select * from Chapter where bookId=:bookId and `index` =:index")
    fun getChapterByIndex(bookId: String, index: Int): Chapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapter(chapterList: List<Chapter>): List<Long>

    @Query("delete from Chapter where bookId=:bookId")
    fun deleteChapterByBookId(bookId: String)

    @Query("delete from Chapter where bookId in (select id from Book where title=:bookTitle)")
    fun deleteChapterByBookTitleAndAuthor(bookTitle: String): Int

    @Query("update Chapter set isLoaded=1 where bookId=:bookId and (bookId+`index`) in (select (bookId+chapterIndex) from ChapterContent where bookId=:bookId)")
    fun updateBookChapterIsLoaded(bookId: String)

    //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<ChapterContent>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapter(chapter: Chapter, chapterContent: ChapterContent)

    @Query("select * from ChapterContent where bookId=:bookId and chapterIndex=:chapterIndex")
    fun getChapterContentFlow(bookId: String, chapterIndex: Int): Flow<ChapterContent?>

    @Query("select * from ChapterContent where bookId=:bookId and chapterIndex=:chapterIndex")
    fun getChapterContent(bookId: String, chapterIndex: Int): ChapterContent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapterContent(chapterContent: ChapterContent): Long

    @Query("delete from ChapterContent where bookId in (select id from Book where title=:bookTitle)")
    fun deleteChapterContentByBookTitleAndAuthor(bookTitle: String): Int

    @Query("delete from ChapterContent where bookId=:bookId")
    fun deleteChapterContentByBookId(bookId: String)

    @Query("select * from ReadingRecord where bookTitle=:bookTitle")
    fun getReadingRecordFlow(bookTitle: String): Flow<ReadingRecord?>

    @Query("select * from ReadingRecord where bookTitle=:bookTitle")
    fun getReadingRecord(bookTitle: String): ReadingRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReadingRecord(record: ReadingRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReadingRecordList(record: List<ReadingRecord>): List<Long>

    @Query("select * from ReadingRecord  order by bookId")
    fun getAllReadingRecord(): Flow<List<ReadingRecord>>

    @Query("delete from ReadingRecord where bookTitle=:bookTitle")
    fun deleteReadingRecord(bookTitle: String): Int

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
    @Query("delete from Book where title not in (select bookTitle from ReadingRecord)")
    fun cleanBook(): Int

    @Query("delete from ReadingRecord where bookId not in (select id from Book)")
    fun cleanReadingBook(): Int

    @Query("delete from Chapter where bookId not in (select id from Book)")
    fun cleanChapter(): Int

    @Query("delete from ChapterContent where bookId not in (select id from Book)")
    fun cleanChapterContent(): Int
}