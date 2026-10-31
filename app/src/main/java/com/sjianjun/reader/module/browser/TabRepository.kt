package com.sjianjun.reader.module.browser

import com.sjianjun.reader.App
import com.sjianjun.reader.bean.BrowserTabEntity
import com.sjianjun.reader.repository.DbFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 浏览器标签页列表的轻量单例（唯一真相源）。
 * 只保存列表元数据，**不含任何 WebView 引用、不含导航/多路复用逻辑**。
 * WebView 实例只存在于各自的 TabFragment 内。
 *
 * 持久化：内存列表为准，逐条 upsert 仍存在的标签、按主键删除已关闭的标签，绝不清库全量覆盖。
 */
object TabRepository {

    private val tabs = mutableListOf<BrowserTab>()
    var activeId: String? = null
        private set

    @Volatile
    private var loaded = false

    /** 首次访问时从数据库恢复标签列表（只恢复元数据，WebView 在需要时按 tab.url 重建） */
    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val entities = runBlocking(Dispatchers.IO) { DbFactory.db.browserDataDao().getAllTabs() }
        tabs.clear()
        tabs.addAll(entities.mapNotNull { it.toTab() })
        activeId = entities.firstOrNull { it.isActive }?.id ?: tabs.firstOrNull()?.id
        if (activeId !in tabs.map { it.id }) activeId = tabs.firstOrNull()?.id
    }

    val list: List<BrowserTab> get() = tabs.toList()
    val size: Int get() = tabs.size
    fun get(id: String?): BrowserTab? = if (id == null) null else tabs.firstOrNull { it.id == id }
    fun getActive(): BrowserTab? = get(activeId)
    fun indexOf(id: String?): Int = tabs.indexOfFirst { it.id == id }

    fun add(tab: BrowserTab, makeActive: Boolean = false) {
        tabs.add(tab)
        if (makeActive) activeId = tab.id
    }

    fun remove(id: String): BrowserTab? {
        val idx = indexOf(id)
        if (idx < 0) return null
        val removed = tabs.removeAt(idx)
        if (activeId == id) activeId = tabs.lastOrNull()?.id
        return removed
    }

    fun setActive(id: String?) {
        if (id == null || tabs.any { it.id == id }) activeId = id
    }

    /** 把 [id] 移动到 [toIndex]（标签网格拖动排序） */
    fun move(id: String, toIndex: Int) {
        val from = indexOf(id)
        if (from < 0 || toIndex !in tabs.indices) return
        val tab = tabs.removeAt(from)
        tabs.add(toIndex, tab)
        activeId = activeId // 保持不变
    }

    /** 将内存中的某标签元数据同步到内存列表顺序（拖动后刷新 active 标记用） */
    fun reorder(orderedIds: List<String>) {
        val map = tabs.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { map[it] }
        if (reordered.size == tabs.size) {
            tabs.clear()
            tabs.addAll(reordered)
        }
    }

    /** 落库：以内存缓存为准，逐条 upsert 仍存在的标签，并删除库中存在但内存已无的标签。 */
    fun persist() {
        if (tabs.isEmpty()) return
        val dao = DbFactory.db.browserDataDao()
        val present = tabs.mapIndexed { i, tab -> tab.toEntity(i, tab.id == activeId) }
        val presentIds = present.map { it.id }
        runBlocking(Dispatchers.IO) {
            dao.deleteTabsNotIn(presentIds)
            present.forEach { dao.insertTab(it) }
        }
    }

    fun clearPersisted() {
        runBlocking(Dispatchers.IO) { DbFactory.db.browserDataDao().clearTabs() }
    }

    /** 清空内存单例（标签列表与 loaded 标记），用于冷启动/调试场景让下次访问重新从库加载 */
    fun reset() {
        tabs.clear()
        activeId = null
        loaded = false
    }

    /** 解除所有子标签与来源页的链接（主动退出时调用），使已存在的父子/来源关系相互独立 */
    fun unlinkAll() {
        for (t in tabs) if (t.sourceTabId.isNotBlank()) t.sourceTabId = ""
    }

    /** 删除某标签的缩略图文件（与 TabStateStore 写入路径一致） */
    fun deleteThumb(tab: BrowserTab) {
        runCatching { File(App.app.cacheDir, "browser_tabs/${tab.id}.png").delete() }
        tab.thumbPath = null
    }

    /** 删除某标签的 WebView 状态快照文件 */
    fun deleteState(tab: BrowserTab) {
        runCatching { File(App.app.cacheDir, "browser_tabs/${tab.id}.state").delete() }
        tab.state = null
        tab.stateUrl = null
    }

    /**
     * 退出独立浏览器时清理“外部指定网址自动创建（transient）”的临时标签：
     *  - 返回栈到底（无原生后退历史）的临时标签 → 直接移除；
     *  - 仍有前进历史的临时标签 → 转为普通标签（清除 transient 标记）。
     * 同时按主键删除被移除标签的库记录、缩略图与状态文件。
     */
    fun finalizeTransientTabs(): List<BrowserTab> {
        if (tabs.none { it.transient }) return emptyList()
        val removed = mutableListOf<BrowserTab>()
        for (i in tabs.indices.reversed()) {
            val tab = tabs[i]
            if (!tab.transient) continue
            if (tab.state?.isEmpty == false || tab.url.isNotBlank()) {
                // 简化处理：退出时仍保留内容的临时标签转为普通标签，避免“退出即丢页面”的突兀感
                tab.transient = false
            } else {
                removed.add(tab)
                tabs.removeAt(i)
            }
        }
        if (activeId !in tabs.map { it.id }) activeId = tabs.lastOrNull()?.id
        removed.forEach {
            deleteThumb(it)
            deleteState(it)
            runBlocking(Dispatchers.IO) { DbFactory.db.browserDataDao().deleteTab(it.id) }
        }
        return removed
    }
}

private fun BrowserTabEntity.toTab(): BrowserTab = BrowserTab(
    id = id,
    title = title,
    url = url,
    thumbPath = thumbPath,
    transient = transient,
    lastUsedAt = lastUsedAt,
    sourceTabId = sourceTabId,
)

private fun BrowserTab.toEntity(orderIndex: Int, isActive: Boolean): BrowserTabEntity =
    BrowserTabEntity(
        id = id,
        title = title,
        url = url,
        orderIndex = orderIndex,
        isActive = isActive,
        thumbPath = thumbPath,
        historyJson = "",
        historyIndex = -1,
        lastUsedAt = lastUsedAt,
        transient = transient,
        sourceTabId = sourceTabId,
    )
