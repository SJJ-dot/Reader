package com.sjianjun.reader.repository.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sjianjun.reader.bean.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBook(bookList: List<Book>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBook(book: Book): Long

    @Query("delete from Book where title=:title")
    fun deleteBook(title: String): Int

    @Query("delete from Book where id=:id")
    fun deleteBookById(id: String)

    @Update
    fun updateBook(book: Book)

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
    fun getAllSourceBooksByTitle(title: String): Flow<List<Book>>

    @Query("select * from Book where id in (select bookId from ReadingRecord where bookTitle=:title)")
    fun getReadingBook(title: String): Flow<Book?>
    //有空再改连表查询吧
    @Query("delete from Book where title not in (select bookTitle from ReadingRecord)")
    fun cleanBook(): Int

    /**
     * 重置所有书籍的加载状态。应用启动时调用，避免异常退出后 isLoading 残留为 true 导致书籍无法加载
     */
    @Query("update Book set isLoading=0 where isLoading=1")
    fun resetBookLoadingState(): Int

    /**
     * 重置指定书籍的加载状态。页面销毁时调用，避免加载被中断后 isLoading 残留为 true
     */
    @Query("update Book set isLoading=0 where isLoading=1 and id in (:bookIds)")
    fun resetBookLoadingState(bookIds: List<String>): Int

    /**
     * 重置同名（不同书源）书籍的加载状态
     */
    @Query("update Book set isLoading=0 where isLoading=1 and title=:title")
    fun resetBookLoadingStateByTitle(title: String): Int
}