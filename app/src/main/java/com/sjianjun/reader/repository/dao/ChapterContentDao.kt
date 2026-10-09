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

    /**
     * 章节内容以章节链接(chapterUrl)定位，章节索引重排（补齐缺章、插入新章）后内容不会错位
     */
    @Query("select * from ChapterContent where bookId=:bookId and chapterUrl=:chapterUrl order by pageIndex")
    fun getChapterContent(bookId: String, chapterUrl: String): Flow<List<ChapterContent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(chapterContent: ChapterContent): Long

    @Query("delete from ChapterContent where bookId in (select id from Book where title=:bookTitle)")
    fun deleteChapterContentByBookTitleAndAuthor(bookTitle: String): Int

    @Query("delete from ChapterContent where bookId=:bookId")
    fun deleteChapterContentByBookId(bookId: String)

    @Query("delete from ChapterContent where bookId not in (select id from Book)")
    fun cleanChapterContent(): Int

    /**
     * 清理没有对应章节的内容：章节被删除或章节链接变更后遗留的脏数据。
     * 不清掉的话，这些内容会一直占用空间，甚至被后续章节命中
     */
    @Query("delete from ChapterContent where not exists (select 1 from Chapter where Chapter.bookId=ChapterContent.bookId and Chapter.url=ChapterContent.chapterUrl)")
    fun cleanOrphanChapterContent(): Int

    /**
     * 清理章节链接为空的内容，无法定位到具体章节，属于脏数据
     */
    @Query("delete from ChapterContent where trim(chapterUrl) = ''")
    fun cleanBlankUrlChapterContent(): Int
}
