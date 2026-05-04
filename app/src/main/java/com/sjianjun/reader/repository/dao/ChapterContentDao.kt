package com.sjianjun.reader.repository.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sjianjun.reader.bean.ChapterContent
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterContentDao {
    //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<ChapterContent>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


    @Query("select * from ChapterContent where bookId=:bookId and chapterIndex=:chapterIndex order by pageIndex")
    fun getChapterContent(bookId: String, chapterIndex: Int): Flow<List<ChapterContent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(chapterContent: ChapterContent): Long

    @Query("delete from ChapterContent where bookId in (select id from Book where title=:bookTitle)")
    fun deleteChapterContentByBookTitleAndAuthor(bookTitle: String): Int

    @Query("delete from ChapterContent where bookId=:bookId")
    fun deleteChapterContentByBookId(bookId: String)

    @Query("delete from ChapterContent where bookId not in (select id from Book)")
    fun cleanChapterContent(): Int
}