package com.sjianjun.reader.repository

import androidx.room.withTransaction
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.bean.SourceUrl
import com.sjianjun.reader.preferences.globalConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 订阅链接数据源
 *
 * 订阅链接存储在 Room 数据库中，随数据库一起导出、导入、恢复。
 */
object SourceUrlUseCase {

    private val dao get() = DbFactory.db.sourceUrlDao()

    private val migrateLock = Mutex()

    @Volatile
    private var legacyMigrated = false

    /**
     * 将旧版本存在 MMKV 中的订阅链接迁移到数据库，只执行一次
     */
    private suspend fun ensureLegacyMigrated() {
        if (legacyMigrated) return
        migrateLock.withLock {
            if (legacyMigrated) return@withLock
            val legacy = globalConfig.bookSourceListUser
            if (legacy.isNotEmpty()) {
                saveAllInternal(legacy)
                globalConfig.bookSourceListUser = emptyList()
            }
            legacyMigrated = true
        }
    }

    suspend fun getAll(): List<SourceUrl> = withIo {
        ensureLegacyMigrated()
        dao.getAll()
    }

    suspend fun getAutoUpdate(): List<SourceUrl> = withIo {
        ensureLegacyMigrated()
        dao.getAutoUpdate()
    }

    private suspend fun saveAllInternal(list: List<SourceUrl>) {
        val ordered = list.mapIndexed { index, item -> item.copy(sortOrder = index) }
        DbFactory.db.withTransaction {
            dao.deleteAll()
            dao.insert(ordered)
        }
    }

    /**
     * 用给定顺序整体覆盖订阅链接
     */
    suspend fun saveAll(list: List<SourceUrl>) = withIo {
        ensureLegacyMigrated()
        saveAllInternal(list)
    }

    /**
     * 新增订阅链接，已存在时覆盖并置顶
     */
    suspend fun add(sourceUrl: SourceUrl) = withIo {
        ensureLegacyMigrated()
        val list = dao.getAll().toMutableList()
        list.removeAll { it.url == sourceUrl.url }
        list.add(0, sourceUrl)
        saveAllInternal(list)
    }

    suspend fun delete(url: String) = withIo {
        ensureLegacyMigrated()
        val list = dao.getAll().toMutableList()
        list.removeAll { it.url == url }
        saveAllInternal(list)
    }

    suspend fun setAutoUpdate(url: String, autoUpdate: Boolean) = withIo {
        ensureLegacyMigrated()
        val list = dao.getAll().toMutableList()
        list.find { it.url == url }?.autoUpdate = autoUpdate
        saveAllInternal(list)
    }
}
