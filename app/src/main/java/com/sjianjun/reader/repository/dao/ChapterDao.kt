package com.sjianjun.reader.repository.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sjianjun.reader.bean.Chapter
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("select * from Chapter where bookId=:bookId order by `index` DESC limit 1")
    fun getLastChapterByBookId(bookId: String): Chapter?

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
    fun markChaptersAsLoadedByBookId(bookId: String)

    @Query("delete from Chapter where bookId not in (select id from Book)")
    fun cleanChapter(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(chapter: Chapter)
}