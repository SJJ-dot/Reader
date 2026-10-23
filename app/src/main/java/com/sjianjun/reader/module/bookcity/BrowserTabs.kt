package com.sjianjun.reader.module.bookcity

import com.google.gson.Gson
import com.tencent.mmkv.MMKV

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
        var initialUrl: String? = null,
        var userNavigated: Boolean = false,
        var entryCleared: Boolean = false,
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

    private val mmkv = MMKV.mmkvWithID("BrowserTabs")
    private val gson = Gson()

    val tabs = mutableListOf<Tab>()
    var activeIndex: Int = 0

    var onSelect: ((Int) -> Unit)? = null
    var onClose: ((Int) -> Unit)? = null
    var onNew: (() -> Unit)? = null

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
        mmkv.encode("tabs_v3", gson.toJson(saved))
        mmkv.encode("active_v3", activeIndex)
    }

    /** 启动时恢复标签元数据（WebView 在需要时重建） */
    fun restore() {
        if (tabs.isNotEmpty()) return
        val json = mmkv.decodeString("tabs_v3", "[]") ?: "[]"
        val saved = runCatching {
            gson.fromJson(json, Array<SavedTab>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
        if (saved.isEmpty()) return
        runCatching {
            tabs.addAll(saved.map { it.toTab() })
        }.onFailure {
            tabs.clear()
            tabs.addAll(saved.map { it.toTab() })
        }
        activeIndex = mmkv.decodeInt("active_v3", 0).coerceIn(tabs.indices)
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
        mmkv.removeValueForKey("tabs_v3")
        mmkv.removeValueForKey("active_v3")
    }

    /** Activity 重建时旧 WebView 已失效，只清引用，保留标签信息 */
    fun detachWebViews() {
        tabs.forEach { it.webView = null }
    }
}
