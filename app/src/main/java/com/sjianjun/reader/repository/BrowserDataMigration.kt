package com.sjianjun.reader.repository

import com.google.gson.Gson
import com.sjianjun.reader.bean.AdBlockHostEntity
import com.sjianjun.reader.bean.BookmarkEntity
import com.sjianjun.reader.bean.BookmarkFolderEntity
import com.sjianjun.reader.bean.BrowserHistoryEntity
import com.sjianjun.reader.bean.BrowserTabEntity
import com.sjianjun.reader.module.bookcity.BookmarkStore
import com.sjianjun.reader.preferences.AppConfig
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * 把浏览器相关的旧 MMKV 数据一次性迁移到 Room（[Db]）。
 * 在 [Db] 打开后、各数据 object 首次使用前调用；幂等。
 */
object BrowserDataMigration {

    private const val DONE_KEY = "browser_data_migrated_v35"
    private val gson = Gson()

    fun migrateIfNeeded() {
        val mmkv = MMKV.mmkvWithID("BrowserMigration")
        if (mmkv.decodeBool(DONE_KEY, false)) return
        runBlocking(Dispatchers.IO) {
            migrateBookmarks()
            migrateHistory()
            migrateTabs()
            migrateAdBlock()
        }
        mmkv.encode(DONE_KEY, true)
    }

    private fun migrateBookmarks() {
        val dao = DbFactory.db.browserDataDao()
        val old = MMKV.mmkvWithID("BrowserBookmarks")
        val json = old.decodeString("list") ?: return
        val legacy = runCatching {
            gson.fromJson(json, Array<LegacyBookmark>::class.java)?.filterNotNull().orEmpty()
        }.getOrDefault(emptyList())
        val entities = legacy.filter { !it.url.isNullOrBlank() }.mapIndexed { i, b ->
            BookmarkEntity(
                id = UUID.randomUUID().toString(),
                title = (b.title ?: b.url).orEmpty(),
                url = b.url!!,
                folderId = BookmarkStore.ROOT,
                orderIndex = i,
                time = b.time ?: 0L
            )
        }
        dao.insertBookmarks(entities)
        if (dao.getFolder(BookmarkStore.BOOK_SOURCE_FOLDER_ID) == null) {
            dao.insertFolder(
                BookmarkFolderEntity(BookmarkStore.BOOK_SOURCE_FOLDER_ID, "书源", BookmarkStore.ROOT, true)
            )
        }
        old.removeValueForKey("list")
    }

    private fun migrateHistory() {
        val dao = DbFactory.db.browserDataDao()
        val old = MMKV.mmkvWithID("AppConfig_History")
        val json = old.decodeString("History") ?: return
        val legacy = runCatching {
            gson.fromJson(json, Array<LegacyHistory>::class.java)?.filterNotNull().orEmpty()
        }.getOrDefault(emptyList())
        val entities = legacy.filter { !it.url.isNullOrBlank() }
            .map { BrowserHistoryEntity(0, (it.title ?: it.url).orEmpty(), it.url!!, it.time ?: 0L) }
        dao.insertHistories(entities)
        old.removeValueForKey("History")
    }

    private fun migrateTabs() {
        val dao = DbFactory.db.browserDataDao()
        val old = MMKV.mmkvWithID("BrowserTabs")
        val json = old.decodeString("tabs_v3") ?: return
        val active = old.decodeInt("active_v3", 0)
        val saved = runCatching {
            gson.fromJson(json, Array<LegacySavedTab>::class.java)?.filterNotNull().orEmpty()
        }.getOrDefault(emptyList())
        val entities = saved.mapIndexed { i, s ->
            BrowserTabEntity(
                id = s.id,
                title = s.title,
                url = s.url,
                orderIndex = i,
                isActive = i == active,
                thumbPath = s.thumbPath,
                historyJson = gson.toJson(s.history.orEmpty()),
                historyIndex = s.historyIndex ?: -1,
                lastUsedAt = s.lastUsedAt
            )
        }
        dao.insertTabs(entities)
        old.removeValueForKey("tabs_v3")
        old.removeValueForKey("active_v3")
    }

    private fun migrateAdBlock() {
        val dao = DbFactory.db.browserDataDao()
        val config = AppConfig("ad-blacklist1")
        val black = config.hostBlacklist
        val white = config.hostWhitelist
        val entities = mutableListOf<AdBlockHostEntity>()
        black.forEach {
            entities.add(AdBlockHostEntity(it.host, "black", gson.toJson(it.type), it.isPage, it.time, it.mode))
        }
        white.forEach {
            entities.add(AdBlockHostEntity(it.host, "white", gson.toJson(it.type), it.isPage, it.time, it.mode))
        }
        if (entities.isNotEmpty()) dao.insertAdBlock(entities)
        // 清空旧 MMKV 存储，避免重复
        config.hostBlacklist = mutableListOf()
        config.hostWhitelist = mutableListOf()
    }

    private data class LegacyBookmark(val title: String?, val url: String?, val time: Long?)
    private data class LegacyHistory(val title: String?, val url: String?, val time: Long?)
    private data class LegacySavedTab(
        val title: String,
        val url: String,
        val id: String,
        val thumbPath: String?,
        val history: List<String>?,
        val historyIndex: Int?,
        val lastUsedAt: Long
    )
}
