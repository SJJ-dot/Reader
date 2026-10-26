package com.sjianjun.reader.repository.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sjianjun.reader.bean.AdBlockHostEntity
import com.sjianjun.reader.bean.BookmarkEntity
import com.sjianjun.reader.bean.BookmarkFolderEntity
import com.sjianjun.reader.bean.BrowserHistoryEntity
import com.sjianjun.reader.bean.BrowserTabEntity

/** 浏览器相关数据（收藏夹/历史/标签页/广告拦截）的统一访问层 */
@Dao
interface BrowserDataDao {

    /* ===================== 收藏夹文件夹 ===================== */
    @Query("SELECT * FROM bookmark_folder WHERE id = :id")
    fun getFolder(id: String): BookmarkFolderEntity?

    @Query("SELECT * FROM bookmark_folder ORDER BY time ASC")
    fun getAllFolders(): List<BookmarkFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFolder(folder: BookmarkFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFolders(folders: List<BookmarkFolderEntity>)

    @Query("UPDATE bookmark_folder SET name = :name WHERE id = :id")
    fun renameFolder(id: String, name: String)

    @Query("DELETE FROM bookmark_folder WHERE id IN (:ids)")
    fun deleteFolders(ids: List<String>)

    /* ===================== 收藏项 ===================== */
    @Query("SELECT * FROM bookmark WHERE folderId = :folderId ORDER BY orderIndex ASC")
    fun getBookmarks(folderId: String): List<BookmarkEntity>

    @Query("SELECT * FROM bookmark ORDER BY orderIndex ASC")
    fun getAllBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmark WHERE id = :id")
    fun getBookmark(id: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBookmark(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBookmarks(bookmarks: List<BookmarkEntity>)

    @Update
    fun updateBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE id = :id")
    fun deleteBookmark(id: String)

    @Query("DELETE FROM bookmark WHERE folderId IN (:folderIds)")
    fun deleteBookmarksByFolders(folderIds: List<String>)

    @Query("SELECT COUNT(*) FROM bookmark WHERE url = :url")
    fun countByUrl(url: String): Int

    @Query("SELECT COUNT(*) FROM bookmark")
    fun countBookmarks(): Int

    /** 级联删除文件夹（含子孙文件夹及其下所有收藏） */
    @Transaction
    fun removeFoldersCascade(ids: List<String>) {
        val doomed = ids.toMutableSet()
        var grew = true
        while (grew) {
            grew = false
            getAllFolders().forEach { f ->
                if (f.parentId in doomed && f.id !in doomed) {
                    doomed.add(f.id)
                    grew = true
                }
            }
        }
        deleteBookmarksByFolders(doomed.toList())
        deleteFolders(doomed.toList())
    }

    /* ===================== 历史记录 ===================== */
    @Query("SELECT * FROM browser_history ORDER BY time DESC")
    fun getAllHistory(): List<BrowserHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHistory(item: BrowserHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHistories(items: List<BrowserHistoryEntity>)

    @Query("DELETE FROM browser_history WHERE id = :id OR (url = :url AND time = :time)")
    fun deleteHistory(id: Long, url: String, time: Long)

    @Query("DELETE FROM browser_history WHERE url = :url")
    fun deleteHistoryByUrl(url: String)

    @Query("DELETE FROM browser_history")
    fun clearHistory()

    @Query("SELECT COUNT(*) FROM browser_history")
    fun countHistory(): Int

    @Transaction
    fun replaceAllHistory(items: List<BrowserHistoryEntity>) {
        clearHistory()
        insertHistories(items)
    }

    /* ===================== 标签页 ===================== */
    @Query("SELECT * FROM browser_tab ORDER BY orderIndex ASC")
    fun getAllTabs(): List<BrowserTabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTab(tab: BrowserTabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTabs(tabs: List<BrowserTabEntity>)

    @Query("DELETE FROM browser_tab WHERE id = :id")
    fun deleteTab(id: String)

    @Query("DELETE FROM browser_tab")
    fun clearTabs()

    @Query("SELECT COUNT(*) FROM browser_tab")
    fun countTabs(): Int

    @Transaction
    fun replaceAllTabs(tabs: List<BrowserTabEntity>) {
        clearTabs()
        insertTabs(tabs)
    }

    /* ===================== 广告拦截 ===================== */
    @Query("SELECT * FROM ad_block_host WHERE kind = :kind")
    fun getAdBlock(kind: String): List<AdBlockHostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAdBlock(items: List<AdBlockHostEntity>)

    @Query("DELETE FROM ad_block_host WHERE kind = :kind")
    fun clearAdBlock(kind: String)

    @Query("DELETE FROM ad_block_host")
    fun clearAllAdBlock()

    @Query("SELECT COUNT(*) FROM ad_block_host WHERE kind = :kind")
    fun countAdBlock(kind: String): Int

    @Transaction
    fun replaceAdBlock(kind: String, items: List<AdBlockHostEntity>) {
        clearAdBlock(kind)
        insertAdBlock(items)
    }
}
