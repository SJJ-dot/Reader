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

    /**
     * 查询指定序号区间内的章节（不查章节内容），用于换源定位时只在目标位置附近做匹配
     */
    @Query("select * from Chapter where bookId=:bookId and `index` between :startIndex and :endIndex order by `index`")
    suspend fun getChapterListBetween(bookId: String, startIndex: Int, endIndex: Int): List<Chapter>

    @Query("select * from Chapter where title=:title and bookId=:bookId order by `index`")
    fun getChapterByTitle(bookId: String, title: String): List<Chapter>

    @Query("select * from Chapter where bookId=:bookId and title like :name")
    fun getChapterLikeName(bookId: String, name: String): List<Chapter>

    @Query("select * from Chapter where bookId=:bookId and `index` =:index")
    fun getChapterByIndex(bookId: String, index: Int): Chapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapter(chapterList: List<Chapter>): List<Long>

    @Query("delete from Chapter where bookId=:bookId")
    fun deleteChapterByBookId(bookId: String)

    /**
     * 删除index大于指定值的章节。增量更新时丢弃定位章节之后多余的旧章节
     */
    @Query("delete from Chapter where bookId=:bookId and `index` > :index")
    fun deleteChapterAfterIndex(bookId: String, index: Int)

    @Query("delete from Chapter where bookId in (select id from Book where title=:bookTitle)")
    fun deleteChapterByBookTitleAndAuthor(bookTitle: String): Int

    /**
     * 存在**可用**章节内容的标记为已加载（失败内容 contentError=1 不算已缓存）。
     * 按章节链接匹配，章节索引重排后也能正确对应；链接为空的章节无法定位，不做标记
     */
    @Query("update Chapter set isLoaded=1 where bookId=:bookId and trim(url) <> '' and url in (select chapterUrl from ChapterContent where bookId=:bookId and contentError=0)")
    fun markChaptersAsLoadedByBookId(bookId: String)

    /**
     * 清除已加载但没有**可用**内容的章节标记（内容被清理、或只剩失败内容时遗留的脏数据）
     */
    @Query("update Chapter set isLoaded=0 where isLoaded=1 and not exists (select 1 from ChapterContent where ChapterContent.bookId=Chapter.bookId and ChapterContent.chapterUrl=Chapter.url and ChapterContent.contentError=0)")
    fun cleanChapterLoadedState(): Int

    @Query("delete from Chapter where bookId not in (select id from Book)")
    fun cleanChapter(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(chapter: Chapter)
}