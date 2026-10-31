package com.sjianjun.reader.module.browser

/**
 * 跨界面打开链接的持久化请求通道，替代旧版的 EventBus（后台会丢事件）与全局 BrowserTabs.pendingSwitch。
 *
 * 关键性质：**持久字段**，无论浏览器当前是否可见都会被记录，待浏览器回到前台（onResume）时消费，
 * 不再出现“点历史记录/字典没反应”的问题。
 */
object BrowserRequest {

    sealed class Action {
        /** 在浏览器中打开一个 URL：newTab=false 在当前（激活）标签打开，true 新建标签；transient 标记该标签为“外部指定网址打开”，退出时可自动销毁 */
        data class Open(val url: String, val newTab: Boolean, val transient: Boolean = false) : Action()

        /** 阅读模式切回：激活来源标签并加载该 URL */
        data class ReturnToSource(val tabId: String, val url: String) : Action()
    }

    @Volatile
    private var pending: Action? = null

    /** 历史/收藏点击、外部（搜索/字典）打开 */
    fun open(url: String, newTab: Boolean = false, transient: Boolean = false) {
        pending = Action.Open(url, newTab, transient)
    }

    /** 阅读模式切回浏览器 */
    fun returnToSource(tabId: String, url: String) {
        pending = Action.ReturnToSource(tabId, url)
    }

    /** 取出并清空待处理请求（只消费一次） */
    fun consume(): Action? {
        val a = pending
        pending = null
        return a
    }

    fun clear() {
        pending = null
    }
}
