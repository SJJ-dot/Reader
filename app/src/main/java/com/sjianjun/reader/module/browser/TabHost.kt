package com.sjianjun.reader.module.browser

/**
 * 跨标签操作的宿主接口。
 * CustomWebView（每个标签自包含）只管“本标签”的 WebView 与 UI；凡是涉及其它标签或整个浏览器容器的动作
 * （新建/关闭/切换/复制标签、打开标签网格管理、退出、持久化列表、刷新列表 UI）都委托给实现此接口的宿主
 * （即 BrowserFragment）。
 */
interface TabHost {
    /** 本标签的 id */
    val tabId: String

    /** 当前标签总数（用于工具栏角标） */
    val tabCount: Int

    /** 新建一个标签并打开 [url]（空则主页）；transient=true 表示外部自动创建的临时标签；
     *  [sourceTabId] 非空时记录来源页（长按“新标签页打开”使用），子标签返回退出可切回来源页 */
    fun newTab(url: String = "", transient: Boolean = false, sourceTabId: String = "")

    /** 关闭指定标签 */
    fun closeTab(id: String)

    /**
     * 返回键退到栈底继续返回（或工具栏退底叉叉）时关闭“当前（退出）标签页”，总数减一。
     * @return true=浏览器保持打开（仍有其它标签，已切到相邻标签）；false=关掉的是最后一个，浏览器已退出。
     */
    fun closeActiveTab(): Boolean

    /** 切换到指定标签 */
    fun selectTab(id: String)

    /** 复制指定标签（URL/前进后退栈一致） */
    fun duplicateTab(id: String)

    /** 打开标签网格管理界面 */
    fun showTabsManager()

    /** 退出浏览器（内嵌：回到书架；独立 Activity：finish） */
    fun requestExit()

    /**
     * 主动退出（菜单退出 / 切书架等非返回键到底的退出）：先解除所有子标签与来源页的链接
     * （使已存在的父子/来源关系相互独立），再持久化并退出。与返回键关闭标签（保留链接、子标签可切回来源）相反。
     */
    fun activeExit()

    /** 标签元数据变化（url/title/缩略图）后通知宿主刷新列表 UI 并落库 */
    fun notifyTabChanged()

    /** 请求宿主把当前标签列表落库 */
    fun persistTabs()
}
