package com.sjianjun.reader.module.bookcity

import com.google.gson.Gson

/**
 * 每个标签页自维护的前进/后退历史（URL 列表 + 当前索引）。
 *
 * 为什么不用 WebView 原生历史：现代 Android（Chromium WebView）的 `saveState/restoreState`
 * 不保存、也无法恢复前进/后退导航历史（SDK 没有提供注入 BackForwardList 的 API）。
 * 因此历史必须由我们自己维护成 URL 列表，后退/前进通过 `loadUrl` 在列表里移动索引来实现，
 * 这样在“退出再进”甚至“进程被杀”（历史以 JSON 落盘）后前进后退依然可用。
 */
class TabHistory(
    var entries: MutableList<String> = mutableListOf(),
    var currentIndex: Int = -1
) {
    val currentUrl: String? get() = if (currentIndex in entries.indices) entries[currentIndex] else null
    val canGoBack: Boolean get() = currentIndex > 0
    val canGoForward: Boolean get() = currentIndex in 0 until entries.lastIndex

    /** 真实导航（初始加载 / 点击链接 / 地址栏输入）：截断“前进历史”并追加。 */
    fun navigate(url: String) {
        val u = url.takeIf { it.isNotBlank() } ?: return
        if (u == currentUrl) return
        if (currentIndex in 0 until entries.lastIndex) {
            entries.subList(currentIndex + 1, entries.size).clear()
        }
        entries.add(u)
        currentIndex = entries.lastIndex
    }

    /** 后退：移动索引并返回目标 URL；已到栈底返回 null。 */
    fun back(): String? {
        if (!canGoBack) return null
        currentIndex--
        return currentUrl
    }

    /** 前进：移动索引并返回目标 URL；已到栈顶返回 null。 */
    fun forward(): String? {
        if (!canGoForward) return null
        currentIndex++
        return currentUrl
    }

    fun toJson(): String = GSON.toJson(this)

    companion object {
        private val GSON = Gson()
        fun fromJson(json: String?): TabHistory? {
            if (json.isNullOrBlank()) return null
            return runCatching { GSON.fromJson(json, TabHistory::class.java) }.getOrNull()
        }
    }
}
