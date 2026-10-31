package com.sjianjun.reader.module.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.CROSS_ORIGIN_MODE
import com.sjianjun.reader.R
import com.sjianjun.reader.URL
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.module.bookcity.BrowserTabsActivity
import com.sjianjun.reader.module.bookcity.CustomWebView
import com.sjianjun.reader.module.bookcity.WebViewBrowserActivity
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click

/**
 * 浏览器容器 Fragment：内部包含多个标签页 Fragment（[TabFragment]），每个标签都是“完整的单标签浏览器”。
 * 切换标签 = 销毁旧子 Fragment 并重建新子 Fragment（WebView 也随之销毁/重建），避免常驻多个 WebView 占用内存；
 * 标签页的前进后退历史等数据由 [TabFragment] 自身生命周期（onPause/onSaveInstanceState/onDestroyView 落盘、onViewCreated 恢复）负责持久化与恢复。
 *
 * 标签列表的唯一真相源是 [TabRepository]（轻量单例，仅元数据）；WebView 实例只存在于各自 TabFragment 内。
 * 跨界面打开链接走 [BrowserRequest]（持久化通道，不再丢事件）；历史/收藏点击仍走 EventBus（记录页为前台覆盖层）。
 */
class BrowserFragment : BaseFragment(), TabHost {

    private var crossOriginMode: Int = -1

    /** 是否由本容器主动退出（返回键关闭/菜单退出已触发 requestExit）；用于 onStop 区分“被导航切走”与“主动退出” */
    private var selfExiting: Boolean = false

    companion object {
        private const val KEY_RESTORE_TABS = "restore_tabs"
        private const val TAG_PREFIX = "tab_"
        private const val MAX_TABS = 50

        @JvmStatic
        fun newInstance(url: String, crossOriginMode: Int = -1, restoreTabs: Boolean = false) =
            BrowserFragment().apply {
                arguments = Bundle().apply {
                    putString(URL, url)
                    putInt(CROSS_ORIGIN_MODE, crossOriginMode)
                    putBoolean(KEY_RESTORE_TABS, restoreTabs)
                }
            }
    }

    override val tabId: String get() = TabRepository.getActive()?.id ?: ""

    override val tabCount: Int get() = TabRepository.size

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val url = arguments?.getString(URL) ?: globalConfig.bookCityUrl
        val restoreTabs = arguments?.getBoolean(KEY_RESTORE_TABS, false) ?: false
        crossOriginMode = arguments?.getInt(CROSS_ORIGIN_MODE, -1) ?: -1

        TabRepository.ensureLoaded()
        if (TabRepository.size == 0) {
            // 内存/库里都没有标签：
            // - 外部（阅读器/搜索/字典）打开：直接把该 URL 作为首个“可自动销毁”的临时标签；
            // - 书城主页/恢复：建一个空白占位标签，由 loadInitial 落到主页。
            val external = !restoreTabs && url.isNotBlank()
            TabRepository.add(
                BrowserTab(url = if (external) url else (if (restoreTabs) "" else url), transient = external),
                makeActive = true
            )
            syncFragments()
        } else if (!restoreTabs && url.isNotBlank()) {
            // 已有标签列表，外部打开：再开一个“可自动销毁”的临时标签承载该 URL
            addTab(url, transient = true)
        } else {
            // 书城主页 / 恢复上次标签：直接同步并展示当前激活标签
            syncFragments()
        }

        // 历史/收藏点击（记录页为前台覆盖层，EventBus 可可靠送达）
        EventBus.observe(EventKey.OPEN_BROWSER_URL, viewLifecycleOwner, Observer<String> {
            if (it.isNotBlank()) loadInActiveTab(it)
        })
        EventBus.observe(EventKey.OPEN_BROWSER_URL_NEW_TAB, viewLifecycleOwner, Observer<String> {
            if (it.isNotBlank()) newTab(it)
        })

        setOnBackPressed {
            val active = currentTabFragment()
            val rootInsets = ViewCompat.getRootWindowInsets(requireView())
            val imeVisible = rootInsets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            if (imeVisible) {
                androidx.core.view.WindowInsetsControllerCompat(
                    requireActivity().window, requireView()
                ).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                return@setOnBackPressed true
            }
            if (active?.back() == true) {
                true
            } else {
                // 返回键退到栈底继续返回：关闭当前（退出）标签页，总数减一；
                // 若已无其它标签则退出浏览器（空仓库落库，下次打开自动新建空白标签）。
                // 菜单 / 切书架 / 退出按钮等显式退出仍保留全部标签（见 CustomWebView 菜单退出、requestExit）。
                closeActiveTab()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从标签网格（BrowserTabsActivity）返回、或阅读模式/外部唤起时，重新同步标签并消费待处理请求
        syncFragments()
        consumePendingRequests()
    }

    override fun onPause() {
        super.onPause()
        // 子 TabFragment 的 onPause 已自行落盘其 WebView 快照；此处无需再遍历，符合“持久化主要依赖标签自身生命周期”
    }

    override fun onStop() {
        super.onStop()
        // 内嵌模式下，若不是本容器主动退出（返回键关闭/菜单退出），而是被底部导航切到书架等“主动离开”，
        // 视为主动退出：解除子标签与来源页的链接，使两者后续相互独立（子标签返回不再切回来源页）。
        // 仅当 Fragment 正在被移除（isRemoving）且非本容器主动退出时触发；配置变更 / 退后台不触发。
        val act = activity
        if (!selfExiting && act !is WebViewBrowserActivity && isRemoving) {
            TabRepository.unlinkAll()
            TabRepository.persist()
        }
    }

    /** 切到后台：激活标签的 TabFragment 自身 onPause/onSaveInstanceState 已落盘 WebView 快照；此处仅兜底触发一次并落库元数据 */
    fun onAppBackground() {
        currentTabFragment()?.saveStateToDisk()
        TabRepository.persist()
    }

    /** 暂停时为当前激活标签截图（供 Activity onStop/onDestroy 在退出浏览器时兜底） */
    fun captureActiveTabThumb() {
        currentTabFragment()?.captureThumb()
    }

    private fun captureAllThumbs() {
        for (f in childFragmentManager.fragments) {
            (f as? TabFragment)?.captureThumb()
        }
    }

    // ===================== TabHost 实现 =====================

    override fun newTab(url: String, transient: Boolean, sourceTabId: String) {
        if (tabCount >= MAX_TABS) {
            toast("标签页数量已达上限")
            return
        }
        addTab(url, transient, sourceTabId)
    }

    override fun closeTab(id: String) {
        val t = TabRepository.get(id) ?: return
        TabRepository.deleteThumb(t)
        TabRepository.deleteState(t)
        if (TabRepository.size <= 1) {
            // 关掉最后一个：重建一个主页标签，避免空浏览器
            TabRepository.remove(id)
            TabRepository.add(BrowserTab(url = ""), makeActive = true)
        } else {
            TabRepository.remove(id)
        }
        syncFragments()
        TabRepository.persist()
    }

    /**
     * 返回键退到栈底继续返回（或工具栏退底叉叉）时关闭“当前（退出）标签页”：
     *  - 当前标签有来源页（sourceTabId 且仍存在）→ 关闭自身、切回来源页，浏览器保持打开（返回 true）；
     *  - 普通标签（无来源）→ 关闭当前标签并**直接退出浏览器**（不再逐个切到相邻标签再退出）；
     *    其余标签持久化保留，下次打开恢复；若已全部关完则清空仓库，下次打开自动新建空白标签（返回 false）。
     */
    override fun closeActiveTab(): Boolean {
        val active = TabRepository.getActive() ?: return false
        // 子标签（仍链接到来源页）：关闭自身并切回来源页，浏览器保持
        val sourceId = active.sourceTabId.takeIf { it.isNotBlank() }
        if (sourceId != null && TabRepository.get(sourceId) != null) {
            TabRepository.remove(active.id)
            TabRepository.deleteThumb(active)
            TabRepository.deleteState(active)
            TabRepository.setActive(sourceId)
            syncFragments()
            TabRepository.persist()
            return true
        }
        // 普通标签：关闭当前标签并直接退出浏览器（保留其余标签，清空则下次新建空白）
        TabRepository.remove(active.id)
        TabRepository.deleteThumb(active)
        TabRepository.deleteState(active)
        if (TabRepository.size == 0) {
            TabRepository.clearPersisted() // 清空 DB，下次打开自动新建空白标签
        } else {
            TabRepository.persist() // 持久化剩余标签，待下次进入恢复
        }
        selfExiting = true
        requestExit()
        return false
    }

    override fun selectTab(id: String) {
        TabRepository.setActive(id)
        syncFragments()
        TabRepository.persist()
    }

    override fun duplicateTab(id: String) {
        val src = TabRepository.get(id) ?: return
        val tab = BrowserTab(
            title = src.title.ifBlank { "新标签页" },
            url = src.url,
            transient = src.transient
        )
        val idx = TabRepository.indexOf(id)
        TabRepository.add(tab, makeActive = true)
        TabRepository.move(tab.id, (idx + 1).coerceAtMost(TabRepository.size - 1))
        syncFragments()
        TabRepository.persist()
    }

    override fun showTabsManager() {
        activity?.startActivity(android.content.Intent(activity, BrowserTabsActivity::class.java))
    }

    override fun requestExit() {
        val act = activity
        if (act is WebViewBrowserActivity) {
            captureAllThumbs()
            act.finish()
        } else {
            // 内嵌在书架：回到书架（避免退出整个 App）
            if (!findNavController().popBackStack(R.id.bookShelfFragment, false)) {
                act?.onBackPressedDispatcher?.onBackPressed()
            }
        }
    }

    /**
     * 主动退出（菜单退出 / 切书架等非返回键到底的退出）：先解除所有子标签与来源页的链接，
     * 使已存在的父子/来源关系相互独立（之后子标签返回不再切回来源页，而是直接退出浏览器），再持久化并退出。
     */
    override fun activeExit() {
        TabRepository.unlinkAll()
        TabRepository.persist()
        selfExiting = true
        requestExit()
    }

    override fun notifyTabChanged() {
        // 标签的 url/title/thumbPath 已就地改在 TabRepository 内的同一 BrowserTab 实例上
        TabRepository.persist()
    }

    override fun persistTabs() {
        TabRepository.persist()
    }

    // ===================== 内部辅助 =====================

    private fun addTab(url: String, transient: Boolean, sourceTabId: String = "") {
        val tab = BrowserTab(url = url, transient = transient, sourceTabId = sourceTabId)
        TabRepository.add(tab, makeActive = true)
        syncFragments()
        TabRepository.persist()
    }

    /**
     * 同步子 Fragment 与 TabRepository：仅保留“激活标签”对应的 Fragment，
     * 其它标签 Fragment 一律销毁（切换标签 = 销毁重建），WebView 不常驻。
     * 标签页数据由 TabFragment 自身生命周期落盘/恢复。
     * 若激活标签 Fragment 已存在则保持不变，避免从标签网格返回 / 旋转时不必要的重建重载。
     */
    private fun syncFragments() {
        val fm = childFragmentManager
        val active = TabRepository.getActive() ?: return
        val activeTag = tagFor(active.id)
        val tx = fm.beginTransaction()
        var activeExists = false
        for (f in fm.fragments.toList()) {
            val id = f.tag?.removePrefix(TAG_PREFIX) ?: continue
            if (id == active.id) {
                activeExists = true
            } else {
                tx.remove(f) // 销毁非激活标签（切换即销毁重建）
            }
        }
        if (!activeExists) {
            tx.add(R.id.tab_container, TabFragment.newInstance(active.id, crossOriginMode), activeTag)
        }
        tx.commitNowAllowingStateLoss()
    }

    private fun tagFor(id: String) = "$TAG_PREFIX$id"

    private fun currentTabFragment(): TabFragment? {
        val active = TabRepository.getActive() ?: return null
        return childFragmentManager.findFragmentByTag(tagFor(active.id)) as? TabFragment
    }

    /** 调试用：取当前激活标签的 CustomWebView（无激活标签返回 null） */
    fun activeWebView(): CustomWebView? = currentTabFragment()?.webView

    private fun loadInActiveTab(url: String) {
        currentTabFragment()?.loadUrl(url)
    }

    private fun loadInTab(tabId: String, url: String) {
        val f = childFragmentManager.findFragmentByTag(tagFor(tabId)) as? TabFragment
        f?.loadUrl(url)
    }

    /** 消费跨界面待处理请求（阅读模式切回 / 外部打开）。供 onResume 与 Activity.onNewIntent 调用 */
    fun consumePendingRequests() {
        val action = BrowserRequest.consume() ?: return
        when (action) {
            is BrowserRequest.Action.Open -> {
                if (action.transient) {
                    newTab(action.url, transient = true)
                } else if (action.newTab) {
                    newTab(action.url)
                } else {
                    loadInActiveTab(action.url)
                }
            }

            is BrowserRequest.Action.ReturnToSource -> {
                if (TabRepository.get(action.tabId) == null) {
                    // 来源标签已关闭：直接新建标签承载阅读页
                    addTab(action.url, transient = false)
                } else {
                    selectTab(action.tabId)
                    loadInTab(action.tabId, action.url)
                }
            }
        }
    }
}
