package com.sjianjun.reader.module.bookcity

/**
 * 标签会话（单一数据源）：
 * - backStack / forwardStack 自维护；
 * - WebView 原生历史只作为 BFCache 加速，不参与逻辑判断；
 * - 导航以"预演 + 提交"的事务方式进行：发起时只改影子栈，加载确认（onPageFinished）后才提交，
 *   取消加载（stopLoading）则回滚，避免异步回调污染真实栈；
 * - navigationEpoch 作为导航序号，事务状态（待提交目标）按标签隔离，标签切换互不影响。
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

    /** 导航代际（序号）：每次导航发起递增，用于标识事务 */
    var navigationEpoch = 0L
        private set

    // ---- 预演（影子栈）：null 表示当前没有待提交的导航 ----
    private var shadowBack: ArrayDeque<String>? = null
    private var shadowForward: ArrayDeque<String>? = null
    private var shadowCurrent: String? = null

    // ---- 待提交事务：目标 URL（null 表示无事务；普通导航目标可为空） ----
    private var pending = false
    private var pendingTarget: String? = null

    /** 当前标签是否有待提交的导航事务 */
    val hasPendingNav: Boolean get() = pending

    private fun ensureShadow() {
        if (shadowBack != null) return
        shadowBack = ArrayDeque(backStack)
        shadowForward = ArrayDeque(forwardStack)
        shadowCurrent = currentUrl
    }

    private fun beginPending(target: String?) {
        pending = true
        pendingTarget = target
        navigationEpoch++
    }

    /** 预演后退：返回目标；null 表示已退到底。只改影子栈，不提交。 */
    fun beginBack(): String? {
        ensureShadow()
        val back = shadowBack!!
        val forward = shadowForward!!
        val current = shadowCurrent!!
        while (back.isNotEmpty()) {
            val target = back.removeLast()
            if (samePage(target, current)) continue
            if (forward.isEmpty() || forward.lastOrNull() != current) {
                forward.addLast(current)
            }
            shadowCurrent = target
            beginPending(target)
            return target
        }
        shadowCurrent = current
        return null
    }

    /** 预演前进：返回目标；null 表示已到顶。只改影子栈，不提交。 */
    fun beginForward(): String? {
        ensureShadow()
        val back = shadowBack!!
        val forward = shadowForward!!
        val current = shadowCurrent!!
        while (forward.isNotEmpty()) {
            val target = forward.removeLast()
            if (samePage(target, current)) continue
            back.addLast(current)
            shadowCurrent = target
            beginPending(target)
            return target
        }
        shadowCurrent = current
        return null
    }

    /** 预演新导航：当前页压入后退栈。只改影子栈，不提交。 */
    fun beginNavigate(url: String) {
        if (url.isBlank()) return
        ensureShadow()
        val back = shadowBack!!
        val forward = shadowForward!!
        val current = shadowCurrent!!
        if (current.isNotBlank() && !samePage(current, url)) {
            back.addLast(current)
            forward.clear()
        }
        shadowCurrent = url
        beginPending(url)
    }

    /**
     * 判断一次加载完成（onPageFinished 的 url）是否属于当前待提交事务。
     * @param redirect 是否通过重定向到达（此时实际 URL 会不同于发起目标，放宽匹配）。
     */
    fun isCurrentNav(actualUrl: String?, redirect: Boolean): Boolean {
        if (!pending || actualUrl.isNullOrBlank()) return false
        val target = pendingTarget
        return target == null || samePage(target, actualUrl) || redirect
    }

    /**
     * 提交预演：真实栈同步为影子栈。
     * @param actualUrl 实际加载完成的 URL（重定向后可能不同于发起时的目标）。
     */
    fun commit(actualUrl: String?) {
        val back = shadowBack
        val forward = shadowForward
        if (back == null || forward == null) {
            clearShadow()
            clearPending()
            return
        }
        backStack.clear()
        backStack.addAll(back)
        forwardStack.clear()
        forwardStack.addAll(forward)
        currentUrl = actualUrl?.takeIf { it.isNotBlank() } ?: shadowCurrent ?: currentUrl
        clearShadow()
        clearPending()
    }

    /** 回滚预演：丢弃影子栈，真实栈保持不变。 */
    fun rollback() {
        clearShadow()
        clearPending()
    }

    private fun clearShadow() {
        shadowBack = null
        shadowForward = null
        shadowCurrent = null
    }

    private fun clearPending() {
        pending = false
        pendingTarget = null
    }

    // ---- 直接栈操作（用于恢复、无事务兜底等场景） ----

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

    /** canBack/canForward 优先反映预演中的状态，便于 UI 乐观更新 */
    val canBack: Boolean get() = (shadowBack ?: backStack).isNotEmpty()
    val canForward: Boolean get() = (shadowForward ?: forwardStack).isNotEmpty()

    fun samePage(a: String?, b: String?): Boolean =
        a != null && b != null &&
                a.substringBefore('#').trimEnd('/') == b.substringBefore('#').trimEnd('/')
}
