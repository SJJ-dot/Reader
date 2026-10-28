package com.sjianjun.reader.module.bookcity

import com.google.gson.Gson
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.BrowserTabEntity
import com.sjianjun.reader.repository.DbFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

/** 浏览器标签页（进程内共享，供 CustomWebView 与标签页界面使用） */
object BrowserTabs {

    data class Tab(
        var title: String,
        var url: String,
        var webView: MyWebView? = null,
        val id: java.util.UUID = java.util.UUID.randomUUID(),
        var thumbPath: String? = null,
        var fromPopup: Boolean = false,
        var state: android.os.Bundle? = null,
        val history: MutableList<String> = mutableListOf(),
        var historyIndex: Int = -1,
        var restored: Boolean = false,
        var lastUsedAt: Long = System.currentTimeMillis(),
        /** 单一数据源：前进/后退栈与当前页都以此为准 */
        val session: TabSession = TabSession(),
        /** 是否由“外部指定网址打开”自动创建：退出时若返回栈到底则自动关闭，否则转为普通标签 */
        var transient: Boolean = false,
    ) {
        fun syncToSession() {
            session.currentUrl = url
            session.title = title
            session.lastUsedAt = lastUsedAt
        }
    }

    private data class SavedTab(
        val title: String,
        val url: String,
        val id: String,
        val thumbPath: String? = null,
        val history: List<String>? = null,
        val historyIndex: Int? = null,
        val lastUsedAt: Long = 0L,
        val transient: Boolean = false,
    )

    private val gson = Gson()

    val tabs = mutableListOf<Tab>()
    var activeIndex: Int = 0

    var onSelect: ((Int) -> Unit)? = null
    var onClose: ((Int) -> Unit)? = null
    var onNew: (() -> Unit)? = null
    var onDuplicate: ((Int) -> Unit)? = null

    /** 阅读模式切回浏览器时由阅读模式写入、浏览器 onResume 消费：
     *  切到来源标签页并加载该 URL（进程内共享，避免 Intent/singleTask 时序问题） */
    var pendingSwitchTabId: String? = null
    var pendingSwitchUrl: String? = null

    /**
     * 落库：以内存缓存为准，逐条 upsert 仍存在的标签，并删除库中存在但内存已无的标签。
     * 绝不做"清库全量覆盖"——仅当内存本身为空（用户已关闭全部标签）时跳过写入，
     * 此时 DB 已由各关闭操作逐条删除，不会误清导致标签页消失。
     */
    fun persist() {
        if (tabs.isEmpty()) return
        val dao = DbFactory.db.browserDataDao()
        val present = tabs.mapIndexed { i, tab -> tab.toEntity(i, i == activeIndex) }
        val presentIds = present.map { it.id }
        runBlocking(Dispatchers.IO) {
            dao.deleteTabsNotIn(presentIds)
            present.forEach { dao.insertTab(it) }
        }
    }

    /** 把一个内存标签转成可持久化的实体（标题/URL/前进后退栈/缩略图/激活标记） */
    private fun Tab.toEntity(orderIndex: Int, isActive: Boolean): BrowserTabEntity {
        val back = session.backStack.toList()
        val forward = session.forwardStack.toList()
        val current = session.currentUrl.ifBlank { url }
        val history = back + current + forward
        val index = back.size
        return BrowserTabEntity(
            id = id.toString(),
            title = title,
            url = current,
            orderIndex = orderIndex,
            isActive = isActive,
            thumbPath = thumbPath,
            historyJson = gson.toJson(history),
            historyIndex = index,
            lastUsedAt = lastUsedAt,
            transient = transient
        )
    }

    /** 启动时恢复标签元数据（WebView 在需要时重建） */
    fun restore() {
        if (tabs.isNotEmpty()) return
        val entities = runBlocking(Dispatchers.IO) { DbFactory.db.browserDataDao().getAllTabs() }
        if (entities.isEmpty()) return
        runCatching {
            tabs.addAll(entities.mapNotNull { entity ->
                val history = runCatching {
                    gson.fromJson(entity.historyJson, Array<String>::class.java)?.toList().orEmpty()
                }.getOrDefault(emptyList())
                val saved = SavedTab(
                    entity.title, entity.url, entity.id, entity.thumbPath,
                    history, entity.historyIndex, entity.lastUsedAt, entity.transient
                )
                saved.toTab()
            })
        }
        activeIndex = entities.indexOfFirst { it.isActive }.coerceIn(tabs.indices)
        if (activeIndex !in tabs.indices) activeIndex = 0
    }

    private fun SavedTab.toTab(): Tab {
        val tab = Tab(
            title, url,
            id = runCatching { java.util.UUID.fromString(id) }.getOrDefault(java.util.UUID.randomUUID()),
            thumbPath = thumbPath,
            lastUsedAt = if (lastUsedAt > 0) lastUsedAt else System.currentTimeMillis(),
        )
        val list = history.orEmpty()
        val idx = (historyIndex ?: -1).coerceIn(-1, list.lastIndex)
        val back = list.take((idx + 1).coerceAtLeast(0))
        if (back.isNotEmpty()) {
            tab.session.backStack.addAll(back.dropLast(1))
            tab.url = back.last()
        }
        tab.session.forwardStack.addAll(list.drop(back.size))
        tab.session.currentUrl = url
        tab.session.title = title
        tab.restored = true
        tab.history.addAll(list)
        tab.historyIndex = idx
        tab.transient = transient
        return tab
    }

    fun clearPersisted() {
        runBlocking(Dispatchers.IO) { DbFactory.db.browserDataDao().clearTabs() }
    }

    /** Activity 重建时旧 WebView 已失效，只清引用，保留标签信息 */
    fun detachWebViews() {
        tabs.forEach { it.webView = null }
    }

    /** 删除某标签的缩略图文件（与 CustomWebView 写入路径一致） */
    fun deleteThumb(tab: Tab) {
        runCatching { File(App.app.cacheDir, "browser_tabs/${tab.id}.png").delete() }
        tab.thumbPath = null
    }

    /**
     * 退出独立浏览器时清理“外部指定网址自动创建（transient）”的临时标签：
     *  - 返回栈到底（[TabSession.backStack] 为空）的临时标签 → 直接移除，实现“退出即自动关闭”；
     *  - 仍有前进历史的临时标签 → 转为普通标签（清除 transient 标记），此后与用户手动开启一致，
     *    除非手动关闭否则不再自动关闭。
     * 同时按主键删除被移除标签的库记录与缩略图，保证下次进入不会“复活”。
     */
    fun finalizeTransientTabs() {
        if (tabs.none { it.transient }) return
        val removed = mutableListOf<Tab>()
        for (i in tabs.indices.reversed()) {
            val tab = tabs[i]
            if (!tab.transient) continue
            if (tab.session.backStack.isEmpty()) {
                removed.add(tab)
                tabs.removeAt(i)
                if (i < activeIndex) activeIndex--
                else if (i == activeIndex) activeIndex = activeIndex.coerceAtMost(tabs.lastIndex)
            } else {
                tab.transient = false
            }
        }
        if (activeIndex !in tabs.indices) activeIndex = tabs.lastIndex.coerceAtLeast(0)
        if (removed.isNotEmpty()) {
            runBlocking(Dispatchers.IO) {
                val dao = DbFactory.db.browserDataDao()
                removed.forEach { dao.deleteTab(it.id.toString()) }
            }
            removed.forEach { deleteThumb(it) }
        }
    }
}
