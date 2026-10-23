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
    )

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

    /** 本次退出是否由后退到底触发：是则下次进入不恢复标签 */
    var exitByBack: Boolean = false

    var onSelect: ((Int) -> Unit)? = null
    var onClose: ((Int) -> Unit)? = null
    var onNew: (() -> Unit)? = null

    /** 保存标签列表（标题/URL/选中项），下次进入浏览器恢复 */
    fun persist() {
        val saved = tabs.map {
            SavedTab(
                it.title, it.url, it.id.toString(), it.thumbPath,
                it.history.toList(), it.historyIndex, it.lastUsedAt
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
            tabs.addAll(saved.map {
                Tab(
                    it.title, it.url,
                    id = java.util.UUID.fromString(it.id),
                    thumbPath = it.thumbPath,
                    history = it.history.orEmpty().toMutableList(),
                    historyIndex = it.historyIndex ?: -1,
                    lastUsedAt = it.lastUsedAt,
                    restored = true
                )
            })
        }.onFailure {
            tabs.clear()
            tabs.addAll(saved.map {
                Tab(
                    it.title, it.url,
                    thumbPath = it.thumbPath,
                    history = it.history.orEmpty().toMutableList(),
                    historyIndex = it.historyIndex ?: -1,
                    lastUsedAt = it.lastUsedAt,
                    restored = true
                )
            })
        }
        activeIndex = mmkv.decodeInt("active_v3", 0).coerceIn(tabs.indices)
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
