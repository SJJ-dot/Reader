package com.sjianjun.reader.module.bookcity

import com.google.gson.Gson
import com.sjianjun.reader.bean.BrowserTabEntity
import com.sjianjun.reader.repository.DbFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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

    /** 保存标签列表（标题/URL/前进后退栈/选中项），下次进入浏览器恢复 */
    fun persist() {
        val saved = tabs.map { tab ->
            val back = tab.session.backStack.toList()
            val forward = tab.session.forwardStack.toList()
            val current = tab.session.currentUrl.ifBlank { tab.url }
            val history = back + current + forward
            val index = back.size
            SavedTab(
                tab.title, current, tab.id.toString(), tab.thumbPath,
                history, index, tab.lastUsedAt
            )
        }
        val dao = DbFactory.db.browserDataDao()
        val entities = saved.mapIndexed { i, s ->
            BrowserTabEntity(
                id = s.id, title = s.title, url = s.url, orderIndex = i, isActive = i == activeIndex,
                thumbPath = s.thumbPath, historyJson = gson.toJson(s.history.orEmpty()),
                historyIndex = s.historyIndex ?: -1, lastUsedAt = s.lastUsedAt
            )
        }
        runBlocking(Dispatchers.IO) { dao.replaceAllTabs(entities) }
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
                    history, entity.historyIndex, entity.lastUsedAt
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
        return tab
    }

    fun clearPersisted() {
        runBlocking(Dispatchers.IO) { DbFactory.db.browserDataDao().clearTabs() }
    }

    /** Activity 重建时旧 WebView 已失效，只清引用，保留标签信息 */
    fun detachWebViews() {
        tabs.forEach { it.webView = null }
    }
}
