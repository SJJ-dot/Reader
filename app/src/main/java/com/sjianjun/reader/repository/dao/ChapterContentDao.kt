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
     * 章节内容以章节链接(chapterUrl)定位，章节索引重排（补齐缺章、插入新章）后内容不会错位。
     *
     * `pageIndex >= 0`：排除缓存失败标记（[ERROR_MARKER_INDEX]，只用于目录显示"部分缓存/失败"，
     * 不参与正文拼接，否则阅读页/TTS 会读到"章节内容加载失败"）。
     */
    @Query("select * from ChapterContent where bookId=:bookId and chapterUrl=:chapterUrl and pageIndex >= 0 order by pageIndex")
    fun getChapterContent(bookId: String, chapterUrl: String): Flow<List<ChapterContent>>

    /**
     * 每章的缓存状态：可用分页数 / 失败分页数（章节目录的缓存标记用）
     */
    @Query(
        """
        select chapterUrl,
               sum(case when contentError = 0 then 1 else 0 end) as validCount,
               sum(case when contentError = 1 then 1 else 0 end) as errorCount
        from ChapterContent
        where bookId = :bookId
        group by chapterUrl
        """
    )
    suspend fun getChapterCacheStatus(bookId: String): List<ChapterCacheStatus>

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

    @Query("delete from ChapterContent where bookId=:bookId and chapterUrl=:chapterUrl and pageIndex=:pageIndex")
    fun deletePage(bookId: String, chapterUrl: String, pageIndex: Int): Int

    companion object {
        /**
         * 缓存失败标记的 pageIndex（负值）：
         * - 不参与正文拼接（[getChapterContent] 过滤 `pageIndex >= 0`）；
         * - 目录据此把"第 1 页成功、后面某页失败"显示成黄色（部分缓存）。
         */
        const val ERROR_MARKER_INDEX = -1
    }
}

/**
 * 一章的缓存状态统计（[ChapterContentDao.getChapterCacheStatus] 的返回项）
 */
data class ChapterCacheStatus(
    val chapterUrl: String,
    /** 可用分页数 */
    val validCount: Int,
    /** 失败分页数（contentError=1） */
    val errorCount: Int,
)
