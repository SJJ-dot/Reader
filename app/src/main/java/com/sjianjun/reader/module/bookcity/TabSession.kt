package com.sjianjun.reader.module.bookcity

/**
 * 标签会话（单一数据源）：
 * - backStack / forwardStack 自维护；
 * - WebView 原生历史只作为 BFCache 加速，不参与逻辑判断；
 * - 持久化只保存本对象的可序列化字段。
 */
class TabSession(
    var currentUrl: String = "",
    var title: String = "",
) {
    val backStack = ArrayDeque<String>()
    val forwardStack = ArrayDeque<String>()

    var webView: MyWebView? = null
    var restored: Boolean = false
    var lastUsedAt: Long = System.currentTimeMillis()
    var thumbPath: String? = null
    val id: String = java.util.UUID.randomUUID().toString()

    /** 新导航：当前页压入后退栈，清空前进栈 */
    fun navigate(url: String) {
        if (url.isBlank()) return
        if (currentUrl.isNotBlank() && !samePage(currentUrl, url)) {
            backStack.addLast(currentUrl)
            // 只有真正前进到新页面，前进栈才失效；导航到同页（如恢复当前页）保留前进栈
            forwardStack.clear()
        }
        currentUrl = url
    }

    /** 后退：跳过与当前页相同的重定向记录，返回 null 表示已到底 */
    fun back(): String? {
        while (backStack.isNotEmpty()) {
            val target = backStack.removeLast()
            if (samePage(target, currentUrl)) continue
            if (forwardStack.isEmpty() || forwardStack.lastOrNull() != currentUrl) {
                forwardStack.addLast(currentUrl)
            }
            currentUrl = target
            return target
        }
        return null
    }

    /** 前进：跳过与当前页相同的记录，返回 null 表示已到顶 */
    fun forward(): String? {
        while (forwardStack.isNotEmpty()) {
            val target = forwardStack.removeLast()
            if (samePage(target, currentUrl)) continue
            backStack.addLast(currentUrl)
            currentUrl = target
            return target
        }
        return null
    }

    val canBack: Boolean get() = backStack.isNotEmpty()
    val canForward: Boolean get() = forwardStack.isNotEmpty()

    private fun samePage(a: String, b: String): Boolean =
        a.substringBefore('#').trimEnd('/') == b.substringBefore('#').trimEnd('/')
}
