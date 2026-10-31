package com.sjianjun.reader.module.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.FragmentTabBinding
import com.sjianjun.reader.module.bookcity.AdBlock
import com.sjianjun.reader.module.bookcity.CustomWebView

/**
 * 单个标签页 Fragment：每个实例就是“一个完整的单标签浏览器”。
 * 拥有自己的 CustomWebView（含 WebView + 工具栏），生命周期独立于其它标签；
 * 切换标签 = 容器对它 show/hide，不会销毁其 WebView（除非整个标签被关闭 / Fragment 视图拆卸）。
 *
 * 跨标签动作通过 [TabHost] 转发给父容器 [BrowserFragment]。
 */
class TabFragment : BaseFragment(), TabHost {

    private lateinit var boundTabId: String
    private var crossOriginMode: Int = -1
    private var customWebView: CustomWebView? = null

    private val host: BrowserFragment? get() = parentFragment as? BrowserFragment

    companion object {
        private const val ARG_TAB_ID = "tab_id"
        private const val ARG_CROSS_ORIGIN = "cross_origin"

        fun newInstance(tabId: String, crossOriginMode: Int = -1): TabFragment =
            TabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TAB_ID, tabId)
                    putInt(ARG_CROSS_ORIGIN, crossOriginMode)
                }
            }
    }

    override val tabId: String get() = boundTabId

    override val tabCount: Int get() = host?.tabCount ?: 1

    override fun newTab(url: String, transient: Boolean, sourceTabId: String) =
        host?.newTab(url, transient, sourceTabId) ?: Unit

    override fun closeTab(id: String) = host?.closeTab(id) ?: Unit

    override fun closeActiveTab(): Boolean = host?.closeActiveTab() ?: false

    override fun selectTab(id: String) = host?.selectTab(id) ?: Unit

    override fun duplicateTab(id: String) = host?.duplicateTab(id) ?: Unit

    override fun showTabsManager() = host?.showTabsManager() ?: Unit

    override fun requestExit() = host?.requestExit() ?: Unit

    override fun activeExit() = host?.activeExit() ?: Unit

    override fun notifyTabChanged() = host?.notifyTabChanged() ?: Unit

    override fun persistTabs() = host?.persistTabs() ?: Unit

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        boundTabId = arguments?.getString(ARG_TAB_ID).orEmpty()
        crossOriginMode = arguments?.getInt(ARG_CROSS_ORIGIN, -1) ?: -1
        return inflater.inflate(R.layout.fragment_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tab = TabRepository.get(boundTabId)
        if (tab == null) {
            // 标签已被关闭：通知父容器移除自身
            host?.closeTab(boundTabId)
            return
        }
        val cwv = com.sjianjun.reader.databinding.FragmentTabBinding.bind(view).customWebView
        customWebView = cwv
        cwv.attach(tab, this)
        cwv.crossOriginOverride = crossOriginMode
        cwv.init(viewLifecycleOwner, AdBlock)
        cwv.loadInitial()
    }

    override fun onResume() {
        super.onResume()
        // 回到前台：若本标签有磁盘快照（前次切走/退出时保存），在 WebView 仍存活时无需重建；
        // 此处仅确保显示正确，加载逻辑已在 loadInitial 完成。
    }

    /** 取本标签的 CustomWebView（供父容器在切换/返回时操作） */
    val webView: CustomWebView? get() = customWebView

    fun loadUrl(url: String) {
        customWebView?.loadUrl(url)
    }

    fun back(): Boolean = customWebView?.back() ?: false

    fun captureThumb() {
        customWebView?.captureActiveTabThumb()
    }

    fun saveStateToDisk() {
        customWebView?.saveStateToDisk()
    }

    override fun onPause() {
        super.onPause()
        // 视图仍存活时先把本标签前进后退历史快照落盘，避免后续拆卸期 saveState 捕获空状态
        customWebView?.saveStateToDisk()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 系统销毁（配置变更 / 进程被杀）时落盘本标签 WebView 快照；
        // 标签切换的 remove 同样会走到这里（Bundle 虽被丢弃，但磁盘写入已生效，loadInitial 仍从磁盘恢复）
        customWebView?.saveStateToDisk()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 离开本标签视图（关闭标签 / 整个浏览器销毁）时释放 WebView，并落盘快照供恢复
        customWebView?.destroyWebView()
        customWebView = null
    }
}
