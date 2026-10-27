package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import android.os.Message
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import android.content.Intent
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import android.view.View
import android.view.GestureDetector
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.sjianjun.reader.bean.WebBook
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.CustomWebViewBinding
import com.sjianjun.reader.databinding.DialogBrowserMenuBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.module.shelf.WebShelfViewModel
import com.sjianjun.reader.databinding.DialogBrowserAddressBinding
import com.sjianjun.reader.preferences.DelegateSharedPref
import com.sjianjun.reader.preferences.dataPref
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.bean.BrowserHistoryEntity
import com.sjianjun.reader.repository.DbFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.init
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.copyToClipboard
import com.sjianjun.reader.preferences.appEssentials
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.hideKeyboard
import com.sjianjun.reader.utils.showKeyboard
import com.sjianjun.reader.utils.webUserAgent
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import com.tencent.mmkv.MMKV
import com.sjianjun.reader.utils.activity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

/*
 * Created by sjj on 2020-07-10
 */
class CustomWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    internal var lifecycleScope: LifecycleCoroutineScope? = null
    var webBook: WebBook? = null
    val binding: CustomWebViewBinding = CustomWebViewBinding.inflate(LayoutInflater.from(context), this, true)
    var adBlock: AdBlock? = null
    val history = MutableLiveData<List<HistoryItem>>()

    /** 单 WebView 实例：所有导航复用它，后退/前进依赖 WebView 原生历史 */
    var webView: MyWebView? = null
        private set
    private val skippedHistoryIndices = linkedMapOf<Int, String>()
    private var linkMenu: PopupWindow? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    /** 当前主框架导航是否通过重定向/无手势跳转到达（重定向源不压栈） */
    private var pendingMainFrameRedirect = false

    /** 底部工具栏是否已收起（页面上滑时收起、下滑时显示） */
    private var toolbarHidden = false

    private companion object {
        const val MAX_TABS = 50

        /** 浏览器历史记录上限（不去重，超出后丢弃最旧的） */
        const val MAX_HISTORY = 1000
        /**
         * 内存中最多同时保留的 WebView 实例数（1 = 只留当前标签）。
         * 其余标签切走后立即销毁 WebView，只保留 URL/标题/前进后退栈与 saveState 快照，切回时重建。
         */
        const val MAX_LIVE_TABS = 1
    }

    private val SEARCH_ENGINES = listOf(
        "百度" to "https://www.baidu.com/s?wd=%s",
        "必应" to "https://cn.bing.com/search?q=%s",
        "谷歌" to "https://www.google.com/search?q=%s",
        "搜狗" to "https://www.sogou.com/web?query=%s",
        "360" to "https://www.so.com/s?q=%s",
        "神马" to "https://m.sm.cn/s?q=%s",
    )

    /** 键盘弹出时工具栏的整体偏移（负值） */
    private var toolbarImeOffset = 0f

    /** 工具栏隐藏进度：0 = 完全显示，height = 完全隐藏 */
    private var toolbarOffset = 0f

    /** 通过标签自带的 URL 历史回退/前进（虚拟 loadUrl），仅用于决定是否清理原生历史 */
    private var virtualNavigating = false

    /** 复用原设置面板的实现（不上界面，只用其功能） */
    private val browserActions by lazy { BrowserActions(context, this) }

    /** 跟手拖动状态 */
    private var toolbarTracking = false
    private var toolbarTouchLastY = 0f
    private var toolbarTouchStartY = 0f
    private var toolbarDragging = false

    /** 触摸滑动阈值（dp 转 px），过滤轻点抖动 */
    private val toolbarTouchSlop by lazy {
        ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    }

    /**
     * 跨域临时配置: -1=未设置(使用globalConfig), 0=询问, 1=允许, 2=拒绝
     */
    @Volatile
    var crossOriginOverride: Int = -1

    /**
     * 菜单「退出」的处理方式。宿主不设置时默认 finish 当前 Activity；
     * 书城内嵌在首页时由 Fragment 设置为「回到书架」，避免把首页 Activity 结束掉。
     */
    var onExitRequest: (() -> Unit)? = null

    fun effectiveCrossOriginMode(): Int {
        return if (crossOriginOverride >= 0) crossOriginOverride else globalConfig.crossOriginMode
    }

    fun init(owner: LifecycleOwner, adBlock: AdBlock, webBook: WebBook?) {
        this.webBook = webBook
        this.adBlock = adBlock

        initView()
        history.value = History.list.toMutableList()
        this.lifecycleScope = owner.lifecycleScope
    }

    /** 把内置搜索引擎的主域加入白名单，避免地址栏搜索被广告拦截误伤 */
    private fun ensureSearchEngineWhitelist() {
        SEARCH_ENGINES
            .mapNotNull { runCatching { it.second.toHttpUrlOrNull()?.topPrivateDomain() }.getOrNull() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { host ->
                if (AdBlock.whitelist.value?.any { it.host == host && it.mode == BLOCK_MODE_DOMAIN } != true) {
                    AdBlock.addWhiteHost(host, BLOCK_MODE_DOMAIN)
                }
            }
    }

    fun loadUrl(url: String) {
        Log.i("loadUrl:$url ")
        showToolbar()
        if (BrowserTabs.tabs.isEmpty()) {
            BrowserTabs.tabs.add(BrowserTabs.Tab("", url))
            BrowserTabs.activeIndex = 0
        }
        val tab = BrowserTabs.tabs[BrowserTabs.activeIndex]
        tab.url = url
        // 新导航：丢弃上一笔未提交的预演，开启新的预演事务
        tab.session.rollback()
        tab.session.beginNavigate(url)
        tab.lastUsedAt = System.currentTimeMillis()
        binding.tabs.text = BrowserTabs.tabs.size.toString()

        val wv = tab.webView?.takeIf { !it.isDestroyed } ?: createWebView().also {
            tab.webView = it
        }
        showOnlyWebView(wv)
        webView = wv
        skippedHistoryIndices.clear()
        wv.currentHost = url
        wv.loadUrl(url)
        updateForwardButton()
    }

    /** 切到后台：保存每个标签的 WebView 状态并销毁，释放内存。
     *  keepActive=true 时保留当前激活标签的 WebView（进程内只留一个），切回时无需重建 */
    fun onBackground(keepActive: Boolean = false) {
        val keepId = if (keepActive) BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)?.id else null
        BrowserTabs.tabs.forEach { tab ->
            if (tab.id == keepId) return@forEach
            val wv = tab.webView ?: return@forEach
            tab.url = wv.url ?: tab.url
            tab.title = wv.title?.takeIf { it.isNotBlank() } ?: tab.title
            // 后台时不重新截图：WebView 不可见，draw 出来会是空白，覆盖之前正常的缩略图
            val state = android.os.Bundle()
            runCatching { wv.saveState(state) }.onSuccess { tab.state = state }
            binding.webViewContainer.removeView(wv)
            wv.destroy()
            tab.webView = null
        }
        if (keepId == null) webView = null
        BrowserTabs.persist()
    }

    /** 回到前台：只恢复当前标签，其余按需恢复 */
    fun onForeground() {
        val tab = BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex) ?: return
        if (tab.webView?.isDestroyed == false) {
            val wv = tab.webView!!
            // 保留的 WebView 仍存活，但若 tab 期望的 URL 与当前显示不一致
            // （如阅读模式切回时已把 tab.url 更新为下一章），必须显式 reload，否则会停在旧页面
            if (tab.url.isNotBlank() && wv.url != tab.url) {
                Log.i("▶SwitchBrowser onForeground webView alive but url mismatch, reload tab.url=${tab.url} cur=${wv.url}")
                wv.currentHost = tab.url
                wv.loadUrl(tab.url)
            } else {
                Log.i("▶SwitchBrowser onForeground webView alive, skip reload (tab.url=${tab.url})")
            }
            return
        }
        Log.i("▶SwitchBrowser onForeground webView destroyed -> reload tab.url=${tab.url} stateNull=${tab.state == null}")
        val wv = createWebView()
        tab.webView = wv
        showOnlyWebView(wv)
        webView = wv
        val state = tab.state
        tab.state = null
        tab.session.rollback() // 旧加载已随 WebView 销毁中断，清理残留事务
        if (state != null) {
            wv.restoreState(state)
        } else if (tab.url.isNotBlank()) {
            wv.currentHost = tab.url
            wv.loadUrl(tab.url)
        }
        binding.tvUrl.text = displayUrl(wv.url ?: tab.url)
        updateForwardButton()
    }

    /** 同一时间只显示当前标签的 WebView（其余 GONE，保留完整状态） */
    private fun showOnlyWebView(target: MyWebView) {
        for (i in 0 until binding.webViewContainer.childCount) {
            val child = binding.webViewContainer.getChildAt(i)
            child.visibility = if (child === target) View.VISIBLE else View.GONE
        }
        if (target.parent == null) {
            binding.webViewContainer.addView(target)
        }
        target.visibility = View.VISIBLE
        trimLiveTabs(target)
    }

    /**
     * 恢复 WebView 的历史/当前页（夜间模式切换等导致 Activity 重建时使用）。
     * 成功返回 true，调用方就不需要再 loadUrl 首页。
     */
    fun restoreState(state: Bundle): Boolean {
        val existing = webView
        val wv = if (existing != null && !existing.isDestroyed) existing else createWebView()
        // 让当前标签持有这个 WebView，避免后续 onBackground/onForeground 找不到它而重复创建
        BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)?.webView = wv
        // restoreState 返回恢复后的历史（失败为 null）
        val restored = runCatching { wv.restoreState(state) != null }.getOrDefault(false)
        if (restored) {
            wv.currentHost = wv.url.orEmpty()
            updateForwardButton()
            Log.i("恢复 WebView 状态：${wv.url}")
        }
        return restored
    }

    private fun createWebView(): MyWebView {
        val wv = MyWebView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            init()
            // 禁止网页自动创建新标签（广告 window.open / target=_blank 一律不响应）
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            webViewClient = createWebViewClient()
            webChromeClient = createWebChromeClient()
        }
        binding.webViewContainer.addView(wv)
        webView = wv
        attachToolbarAutoHide(wv)
        return wv
    }

    /**
     * 只保留当前标签的 WebView：其余标签立即销毁（销毁前保存 URL/标题与 saveState 快照并落盘），
     * 切回时按需重建，用于节省内存。
     */
    private fun trimLiveTabs(active: MyWebView) {
        val live = BrowserTabs.tabs.filter { it.webView != null && it.webView?.isDestroyed == false }
        if (live.size <= MAX_LIVE_TABS) return
        val activeTab = BrowserTabs.tabs.firstOrNull { it.webView === active }
        // 按最近使用时间排序，优先销毁最久未用的
        live.filter { it !== activeTab }
            .sortedBy { it.lastUsedAt }
            .take(live.size - MAX_LIVE_TABS)
            .forEach { tab ->
                val wv = tab.webView ?: return@forEach
                tab.url = wv.url ?: tab.url
                tab.title = wv.title?.takeIf { it.isNotBlank() } ?: tab.title
                val state = android.os.Bundle()
                runCatching { wv.saveState(state) }.onSuccess { tab.state = state }
                binding.webViewContainer.removeView(wv)
                wv.destroy()
                tab.webView = null
            }
        BrowserTabs.persist()
    }

    /**
     * 底部工具栏跟手（仿 Edge）：
     * - 手指上滑：工具栏按滑动距离连续下移（offset 0 → height）；
     * - 手指下滑：连续上移；
     * - 抬手：偏移超过一半 → 自动收起，否则回弹显示。
     */
    private fun attachToolbarAutoHide(wv: MyWebView) {
        val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                // 用长按手势事件的坐标，比按下坐标更准确
                lastTouchX = e.rawX
                lastTouchY = e.rawY
                if (showLinkContextMenu(wv)) {
                    // 主动取消 WebView 原生长按，避免触发默认的链接拖拽 / 上下文菜单
                    wv.cancelLongPress()
                }
            }
        })
        wv.setOnTouchListener { _, event ->
            gesture.onTouchEvent(event)
            handleToolbarTouch(event)
            false
        }
    }

    /** 底部工具栏跟手（仿 Edge）：上滑收起、下滑展开、抬手按阈值吸附 */
    private fun handleToolbarTouch(event: MotionEvent) {
        // 设置面板展开时不联动，避免把输入框拖走
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                binding.browserBookCityToolbar.animate().cancel()
                toolbarTouchStartY = event.rawY
                toolbarTouchLastY = event.rawY
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                toolbarDragging = false
                toolbarTracking = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!toolbarTracking) return
                if (!toolbarDragging) {
                    // 超过触摸阈值才开始跟手，避免轻点/抖动导致工具栏闪动
                    if (abs(event.rawY - toolbarTouchStartY) <= toolbarTouchSlop) return
                    toolbarDragging = true
                    toolbarTouchLastY = event.rawY
                    // 上滑：先把页面铺满，工具栏再下滑，避免底部露出空白
                    if (event.rawY < toolbarTouchStartY) {
                        setContentInsetForToolbarPx(0)
                    }
                } else {
                    val dy = event.rawY - toolbarTouchLastY
                    toolbarTouchLastY = event.rawY
                    if (dy != 0f) dragToolbar(-dy)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (toolbarTracking) {
                    toolbarTracking = false
                    if (toolbarDragging) snapToolbar() else snapToShownIfNeeded()
                    toolbarDragging = false
                }
            }
        }
    }

    /** 跟手拖动：delta > 0 表示向下隐藏 */
    private fun dragToolbar(delta: Float) {
        val bar = binding.browserBookCityToolbar
        val height = toolbarHeightPx()
        val next = (toolbarOffset + delta).coerceIn(0f, height)
        toolbarOffset = next
        if (next < height) {
            // 只要没完全收起就保持可见（从隐藏状态往上滑时会重新露出来）
            toolbarHidden = false
            bar.visibility = View.VISIBLE
        }
        // 只做位移，不做淡出：否则伸缩过程中底栏会半透明，页面内容透出来
        bar.translationY = toolbarImeOffset + next
        bar.alpha = 1f
        // 页面高度已在上滑开始时一次性铺满；这里不再逐帧改高度，避免重排跳动
    }

    /** 抬手吸附：以一半为界 */
    private fun snapToolbar() {
        val bar = binding.browserBookCityToolbar
        val height = toolbarHeightPx()
        val current = toolbarOffset.coerceIn(0f, height)
        val hide = current > height / 2f
        toolbarHidden = hide
        bar.visibility = View.VISIBLE
        val target = if (hide) height else 0f
        // 关键：吸附后同步 offset，避免 insets 等回调把工具栏重置到一半
        toolbarOffset = target
        // 收起时立刻让页面铺满；显示时等工具栏归位后再让位，避免动画中露出空白
        if (hide) setContentInsetForToolbar(false)
        val duration = (120 + 180 * (kotlin.math.abs(target - current) / height)).toLong()
        bar.animate()
            .translationY(toolbarImeOffset + target)
            .alpha(1f)
            .setDuration(duration)
            .withEndAction {
                if (hide) {
                    bar.visibility = View.INVISIBLE
                } else {
                    setContentInsetForToolbar(true)
                }
            }
            .start()
    }

    private fun hideToolbar() {
        if (toolbarHidden) return
        toolbarHidden = true
        setContentInsetForToolbar(false)
        snapToHidden()
    }

    private fun showToolbar() {
        if (!toolbarHidden) return
        toolbarHidden = false
        snapToShown()
    }

    private fun snapToHidden() {
        toolbarOffset = toolbarHeightPx()
        val bar = binding.browserBookCityToolbar
        bar.animate().cancel()
        bar.visibility = View.VISIBLE
        bar.animate()
            .translationY(toolbarImeOffset + toolbarHeightPx())
            .alpha(1f)
            .setDuration(180)
            .withEndAction {
                if (toolbarHidden) bar.visibility = View.INVISIBLE
            }
            .start()
    }

    /** 轻点/微动松手：只把半开的工具栏复位，不动完全收起的状态 */
    private fun snapToShownIfNeeded() {
        if (!toolbarHidden && toolbarOffset > 0f) snapToShown()
    }

    private fun snapToShown() {
        toolbarOffset = 0f
        val bar = binding.browserBookCityToolbar
        bar.animate().cancel()
        bar.visibility = View.VISIBLE
        bar.animate()
            .translationY(toolbarImeOffset)
            .alpha(1f)
            .setDuration(180)
            .withEndAction {
                if (!toolbarHidden) setContentInsetForToolbar(true)
            }
            .start()
    }

    /** 工具栏显示时给网页容器留出底部空间，页面内容不会被遮挡 */
    private fun setContentInsetForToolbar(inset: Boolean) {
        setContentInsetForToolbarPx(if (inset) toolbarHeightPx().toInt() else 0)
    }

    /** 按"工具栏当前露出的高度"设置页面底部留白（拖动时连续更新） */
    private fun setContentInsetForToolbarPx(px: Int) {
        // webViewContainer 被 SwipeRefreshLayout 包裹，SwipeRefreshLayout 忽略子 View 的 margin，
        // 需改设它的 paddingBottom，才能让 WebView 底部为工具栏让位
        val swipe = binding.swipeRefresh
        val target = px.coerceIn(0, toolbarHeightPx().toInt())
        if (swipe.paddingBottom == target) return
        swipe.setPadding(swipe.paddingLeft, swipe.paddingTop, swipe.paddingRight, target)
    }

    private fun toolbarHeightPx(): Float {
        val height = binding.browserBookCityToolbar.height
        return (if (height > 0) height else (48 * resources.displayMetrics.density).toInt()).toFloat()
    }

    private fun applyToolbarTranslation() {
        // 拖动过程中不要被 insets 回调重置
        if (toolbarTracking) return
        val bar = binding.browserBookCityToolbar
        bar.translationY = toolbarImeOffset + toolbarOffset
    }

    fun back(): Boolean {
        val wv = webView ?: return false
        val tab = BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex) ?: return false
        val target = tab.session.beginBack()
        if (target == null) {
            // 后退到底：回滚预演，清空当前标签页会话（重置回主页），退出浏览器。
            // 标签页本身不删除——只有用户手动才能新建/删除标签页，下次进入重建该标签页。
            tab.session.rollback()
            deleteThumb(tab)
            tab.session.backStack.clear()
            tab.session.forwardStack.clear()
            tab.url = homeUrl()
            tab.session.currentUrl = homeUrl()
            tab.webView?.let {
                binding.webViewContainer.removeView(it)
                it.destroy()
            }
            tab.webView = null
            webView = null
            BrowserSession.clear()
            return false
        }
        tab.url = target
        val step = if (wv.canGoBack()) wv.meaningfulBackStep() else 0
        if (step < 0) {
            wv.goBackOrForward(step)
        } else {
            virtualNavigating = true
            wv.currentHost = target
            wv.loadUrl(target)
        }
        updateForwardButton()
        showToolbar()
        BrowserTabs.persist()
        return true
    }

    fun goForward(): Boolean {
        val wv = webView ?: return false
        val tab = BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex) ?: return false
        val target = tab.session.beginForward() ?: return false
        tab.url = target
        val step = if (wv.canGoForward()) wv.meaningfulForwardStep() else 0
        if (step > 0) {
            wv.goBackOrForward(step)
        } else {
            virtualNavigating = true
            wv.currentHost = target
            wv.loadUrl(target)
        }
        updateForwardButton()
        showToolbar()
        BrowserTabs.persist()
        return true
    }

    /** 停止加载：取消当前导航，回滚未提交的预演，栈保持不变 */
    fun stopLoading() {
        webView?.stopLoading()
        BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)?.session?.rollback()
        pendingMainFrameRedirect = false
        updateForwardButton()
    }
    private fun showAddressSheet() {
        // 确保搜索引擎主域在白名单内，避免地址栏搜索被广告拦截
        ensureSearchEngineWhitelist()
        val wv = webView ?: return
        val b = DialogBrowserAddressBinding.inflate(LayoutInflater.from(context))
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(b.root)
        // 用 PAN 而不是 RESIZE：键盘弹出时整窗上移，收键盘时不会卡住 insets，
        // 关闭动画可以立即开始（之前的 0.5 秒停顿就是 RESIZE 等 insets 造成的）
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        b.tvPageTitle.text = wv.title?.takeIf { it.isNotBlank() } ?: displayUrl(wv.url)
        b.tvPageUrl.text = wv.url ?: ""
        // onPageFinished 时 title 可能还没更新，用 document.title 再校正一次
        wv.evaluateJavascript("document.title") { t ->
            val fresh = t?.trim()?.trim('"').orEmpty()
            if (fresh.isNotBlank()) b.tvPageTitle.text = fresh
        }
        // 输入框默认留空：输入内容时按"网址/搜索词"自动判断
        b.urlInput.setText("")

        // 点标题 / 网址复制
        b.tvPageTitle.click { b.tvPageTitle.text.toString().copyToClipboard() }
        b.tvPageUrl.click { b.tvPageUrl.text.toString().copyToClipboard() }

        // 编辑网址：把当前 URL 带入输入框
        b.btnEdit.click {
            b.urlInput.setText(wv.url.orEmpty())
            b.urlInput.requestFocus()
            b.urlInput.setSelection(b.urlInput.text.length)
            b.urlInput.showKeyboard()
        }
        // 设为主页：书城入口/新窗口默认打开这个地址
        b.btnSetHome.click {
            val current = wv.url
            if (current.isNullOrBlank() || !current.startsWith("http")) {
                toast("当前页面无法设为主页")
            } else {
                globalConfig.bookCityUrl = current
                toast("已设为主页")
                dialog.dismiss()
            }
        }
        // 前导标识：搜索词模式显示当前搜索引擎名字，网址模式显示地球图标
        b.btnSearchEngine.text = SEARCH_ENGINES[globalConfig.webSearchEngine.coerceIn(SEARCH_ENGINES.indices)].first
        b.btnSearchEngine.visibility = View.VISIBLE
        b.ivUrlGlobe.visibility = View.GONE
        b.btnSearchEngine.click {
            val names = SEARCH_ENGINES.map { it.first }.toTypedArray()
            MaterialAlertDialogBuilder(context)
                .setTitle("搜索引擎")
                .setSingleChoiceItems(names, globalConfig.webSearchEngine) { d, which ->
                    globalConfig.webSearchEngine = which
                    b.btnSearchEngine.text = names[which]
                    toast("已切换为${names[which]}")
                    d.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        b.urlInput.setOnEditorActionListener { _, actionId, event ->
            val isEnter = actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (actionId == EditorInfo.IME_NULL &&
                            event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
            if (isEnter) {
                val input = b.urlInput.text.toString().trim()
                if (input.isNotBlank()) loadUrl(resolveAddress(input))
                b.urlInput.hideKeyboard()
                dialog.dismiss()
                true
            } else {
                false
            }
        }
        // 关闭（点外部/返回）时先收键盘，避免 insets 回落让 dim 停半秒
        dialog.setOnCancelListener { b.urlInput.hideKeyboard() }
        dialog.setOnShowListener {
            applySheetNavBar(dialog)
            // BottomSheet 自带的白色背景会把卡片的圆角盖住，置为透明只留卡片
            dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // 打开即展开 + 聚焦弹键盘
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            b.urlInput.requestFocus()
            b.urlInput.setSelection(b.urlInput.text.length)
            b.urlInput.post { b.urlInput.showKeyboard() }
        }
        b.urlInput.doAfterTextChanged {
            renderAddressSuggestions(b, dialog, it?.toString().orEmpty())
        }
        dialog.show()
    }

    /** 底部面板打开时：导航栏与面板同色（日间白底黑图标 / 夜间深灰底白图标），随夜间模式切换自动更新 */
    private fun applySheetNavBar(dialog: BottomSheetDialog) {
        val window = dialog.window ?: return
        val decor = window.decorView as? android.view.ViewGroup ?: return

        fun apply() {
            val night = (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            // 日间纯白、夜间深灰
            val color = ContextCompat.getColor(context, R.color.dn_background_card)
            window.navigationBarColor = color
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            // 日间黑色图标、夜间白色图标
            WindowCompat.getInsetsController(window, decor)
                .isAppearanceLightNavigationBars = !night
            // Android 15+ 忽略 navigationBarColor：在导航栏区域盖同色 scrim
            val navBottom = ViewCompat.getRootWindowInsets(decor)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            if (navBottom <= 0) return
            val scrim = decor.findViewWithTag<android.view.View>("browser_sheet_nav")
                ?: android.view.View(context).also { v ->
                    v.tag = "browser_sheet_nav"
                    decor.addView(
                        v,
                        android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT, navBottom
                        ).apply { gravity = android.view.Gravity.BOTTOM }
                    )
                }
            scrim.setBackgroundColor(color)
            scrim.layoutParams = scrim.layoutParams.apply { height = navBottom }
        }

        apply()
        // insets 首次可能尚未就绪（getRootWindowInsets 返回 0），布局后再应用一次
        decor.post { apply() }

        // 面板打开期间切换夜间模式时自动更新；面板关闭时注销，避免泄漏
        val callbacks = object : android.content.ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                apply()
            }

            override fun onLowMemory() {}
        }
        context.applicationContext.registerComponentCallbacks(callbacks)
        decor.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {}

            override fun onViewDetachedFromWindow(v: android.view.View) {
                context.applicationContext.unregisterComponentCallbacks(callbacks)
                decor.removeOnAttachStateChangeListener(this)
            }
        })
    }

    /** 输入时用历史记录做候选，末尾附一条搜索入口 */
    private fun renderAddressSuggestions(
        b: DialogBrowserAddressBinding,
        dialog: BottomSheetDialog,
        query: String,
    ) {
        val container = b.suggestionsList
        container.removeAllViews()
        val q = query.trim()
        // 输入像网址时前导显示地球，否则显示搜索引擎名字
        val urlMode = isUrlInput(q)
        b.btnSearchEngine.visibility = if (urlMode) View.GONE else View.VISIBLE
        b.ivUrlGlobe.visibility = if (urlMode) View.VISIBLE else View.GONE
        if (q.isEmpty()) {
            b.suggestionsScroll.isVisible = false
            return
        }
        b.suggestionsScroll.isVisible = true
        val titleColor = ContextCompat.getColor(context, R.color.dn_text_color_black)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        History.list
            .filter { it.title.contains(q, true) || it.url.contains(q, true) }
            .sortedByDescending { it.time }
            .take(4)
            .forEach { item ->
                val tv = TextView(context).apply {
                    text = item.title.ifBlank { item.url } + "  " + item.url
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    textSize = 13f
                    setTextColor(titleColor)
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    setOnClickListener {
                        loadUrl(item.url)
                        b.urlInput.hideKeyboard()
                        dialog.dismiss()
                    }
                }
                container.addView(tv)
            }
        val searchRow = TextView(context).apply {
            text = "搜索 " + q
            textSize = 13f
            setTextColor(titleColor)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_browser_search, 0, 0, 0)
            compoundDrawablePadding = dp(8)
            setOnClickListener {
                loadUrl(resolveAddress(q))
                b.urlInput.hideKeyboard()
                dialog.dismiss()
            }
        }
        container.addView(searchRow)
    }

    /** 标签页管理界面 */
    /** 标签被关闭/回收时删除它的缩略图文件 */
    private fun deleteThumb(tab: BrowserTabs.Tab) {
        runCatching { File(File(context.cacheDir, "browser_tabs"), tab.id.toString() + ".png").delete() }
        tab.thumbPath = null
    }

    private fun captureThumb(tab: BrowserTabs.Tab) {
        val wv = tab.webView ?: return
        if (wv.width <= 0 || wv.height <= 0 || wv.isDestroyed) return
        runCatching {
            val bmp = Bitmap.createBitmap(wv.width / 2, wv.height / 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.scale(0.5f, 0.5f)
            wv.draw(canvas)
            val dir = File(context.cacheDir, "browser_tabs").apply { mkdirs() }
            val file = File(dir, tab.id.toString() + ".png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 80, it) }
            bmp.recycle()
            tab.thumbPath = file.absolutePath
        }.onFailure { Log.e("标签缩略图失败", it) }
    }

    private fun openTabsManager() {
        captureThumb(BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex) ?: return)
        BrowserTabs.onSelect = { switchTab(it) }
        BrowserTabs.onClose = { closeTab(it) }
        BrowserTabs.onNew = { newTab() }
        BrowserTabs.onDuplicate = { duplicateTab(it) }
        context.startActivity(Intent(context, BrowserTabsActivity::class.java))
    }

    /** 在新标签页打开指定 URL（前台切换，供外部调用） */
    fun openInNewTab(url: String) {
        newTab(url)
    }

    /** 后台打开：新增标签但停留在当前页（长按链接“新标签页打开”使用） */
    private fun openInBackgroundTab(url: String) {
        if (url.isBlank()) return
        ensureTabCapacity()
        val tab = BrowserTabs.Tab("新标签页", url, fromPopup = false).apply {
            session.navigate(url)
        }
        BrowserTabs.tabs.add(tab)
        binding.tabs.text = BrowserTabs.tabs.size.toString()
        BrowserTabs.persist()
        toast("已在后台打开")
    }

    /** 长按链接：命中超链接时弹出上下文菜单，否则交给 WebView 默认处理 */
    private fun showLinkContextMenu(wv: MyWebView): Boolean {
        val result = wv.hitTestResult ?: return false
        val type = result.type
        val isAnchor = type == WebView.HitTestResult.SRC_ANCHOR_TYPE
        val isImageAnchor = type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        if (!isAnchor && !isImageAnchor) return false
        val fallback = result.extra ?: return false
        if (fallback.isBlank()) return false
        // 解析真实链接地址（封面等“链接包裹图片”也能取到书籍地址），再弹菜单
        resolveLinkUrl(wv, fallback) { showLinkMenu(it) }
        return true
    }

    /**
     * 解析长按点的真实链接地址：优先取外层 <a> 的 href（绝对地址，
     * 因此“链接包裹图片”的封面也能拿到书籍地址，与点击行为一致）；取不到则回退原始值。
     */
    private fun resolveLinkUrl(wv: MyWebView, fallback: String, onResolved: (String) -> Unit) {
        val loc = IntArray(2)
        wv.getLocationOnScreen(loc)
        // MotionEvent 坐标为屏幕像素，需换算成 CSS 像素（除以设备密度），
        // 否则高密度屏上坐标整体偏大，elementFromPoint 会偏到错误元素
        val density = resources.displayMetrics.density
        val cssX = ((lastTouchX - loc[0]) / density).toInt()
        val cssY = ((lastTouchY - loc[1]) / density).toInt()
        val js = "(function(){var e=document.elementFromPoint($cssX,$cssY);" +
                "while(e&&e!==document.body){" +
                "if(e.tagName==='A'&&e.href)return e.href;" +
                "e=e.parentNode;}" +
                "return '';})()"
        wv.evaluateJavascript(js) { text ->
            val href = text?.trim('"')?.takeIf { it.isNotBlank() }
            onResolved(href ?: fallback)
        }
    }

    private fun showLinkMenu(url: String) {
        dismissLinkMenu()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val textColor = ContextCompat.getColor(context, R.color.dn_text_color_black)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_popup_menu)
            setPadding(0, dp(6), 0, dp(6))
        }
        fun addItem(label: String, onClick: () -> Unit) {
            container.addView(TextView(context).apply {
                text = label
                textSize = 13f
                setTextColor(textColor)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setOnClickListener {
                    dismissLinkMenu()
                    onClick()
                }
            })
        }
        addItem("新标签页打开") { openInBackgroundTab(url) }
        addItem("复制链接") { url.copyToClipboard() }
        addItem("复制文本") { copyLinkText(url) }
        addItem("添加收藏") {
            BookmarkStore.add(title = "", url = url, folderId = BookmarkStore.ROOT)
            toast("已添加收藏")
        }
        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isFocusable = true
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        popup.setOnDismissListener { linkMenu = null }
        linkMenu = popup
        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val menuW = container.measuredWidth
        val menuH = container.measuredHeight
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val margin = dp(8)
        val gap = dp(4)
        val x = (lastTouchX - menuW / 2f).toInt()
            .coerceIn(margin, (screenW - menuW - margin).coerceAtLeast(margin))
        val yBelow = lastTouchY.toInt() + gap
        val y = if (yBelow + menuH <= screenH - margin) {
            yBelow
        } else {
            (lastTouchY.toInt() - menuH - gap).coerceAtLeast(margin)
        }
        popup.showAtLocation(binding.root, Gravity.NO_GRAVITY, x, y)
    }

    private fun dismissLinkMenu() {
        linkMenu?.dismiss()
        linkMenu = null
    }

    /** 通过 JS 取链接（<a>）的可见文本，供“复制文本”使用 */
    private fun copyLinkText(url: String) {
        val wv = webView ?: return
        val loc = IntArray(2)
        wv.getLocationOnScreen(loc)
        // MotionEvent 坐标为屏幕像素，需换算成 CSS 像素（除以设备密度），
        // 否则高密度屏上坐标整体偏大，elementFromPoint 会偏到错误元素
        val density = resources.displayMetrics.density
        val cssX = ((lastTouchX - loc[0]) / density).toInt()
        val cssY = ((lastTouchY - loc[1]) / density).toInt()
        val js = "(function(){var e=document.elementFromPoint($cssX,$cssY);" +
                "while(e&&e.tagName!=='A'&&e!==document.body&&e.parentNode)e=e.parentNode;" +
                "var t=e&&e.tagName==='A'?(e.textContent||e.innerText||''):'';" +
                "return (t||'').trim();})()"
        wv.evaluateJavascript(js) { text ->
            val cleaned = text?.trim('"')?.takeIf { it.isNotBlank() } ?: url
            cleaned.copyToClipboard()
        }
    }

    private fun homeUrl(): String = webBook?.url ?: globalConfig.bookCityUrl

    /** 标签数达到上限时，回收最久未使用（非当前）的标签，信息已随列表落盘 */
    private fun ensureTabCapacity() {
        if (BrowserTabs.tabs.size < MAX_TABS) return
        val activeIndex = BrowserTabs.activeIndex
        val recycleIndex = BrowserTabs.tabs.indices
            .filter { it != activeIndex }
            .minByOrNull { BrowserTabs.tabs[it].lastUsedAt }
        if (recycleIndex != null) {
            val old = BrowserTabs.tabs.removeAt(recycleIndex)
            deleteThumb(old)
            old.webView?.let {
                binding.webViewContainer.removeView(it)
                it.destroy()
            }
            if (recycleIndex < activeIndex) BrowserTabs.activeIndex--
        }
    }

    /** 复制标签：新建一个 URL/前进后退栈一致的标签并切换过去 */
    private fun duplicateTab(index: Int) {
        if (index !in BrowserTabs.tabs.indices) return
        val src = BrowserTabs.tabs[index]
        val activeTab = BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)
        ensureTabCapacity()
        // ensureTabCapacity 可能回收标签导致索引变化，重新定位被复制标签
        val srcIndex = BrowserTabs.tabs.indexOf(src)
        if (srcIndex < 0) return
        val targetUrl = src.session.currentUrl.ifBlank { src.url }
        val tab = BrowserTabs.Tab(src.title.ifBlank { "新标签页" }, targetUrl, fromPopup = src.fromPopup)
        tab.session.backStack.addAll(src.session.backStack)
        tab.session.forwardStack.addAll(src.session.forwardStack)
        tab.session.currentUrl = targetUrl
        // 新标签插入到被复制标签后面，不切换当前显示（WebView 在切换时按需创建）
        BrowserTabs.tabs.add(srcIndex + 1, tab)
        BrowserTabs.activeIndex = BrowserTabs.tabs.indexOf(activeTab).coerceAtLeast(0)
        binding.tabs.text = BrowserTabs.tabs.size.toString()
        BrowserTabs.persist()
    }

    private fun newTab(url: String = "", fromPopup: Boolean = false) {
        ensureTabCapacity()
        // 新建前先给当前标签截图，否则切走后它的卡片没有缩略图
        BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)?.let { captureThumb(it) }
        BrowserTabs.persist()
        // 新标签页默认打开主页（不再创建空白标签，保证前进后退逻辑统一）
        val targetUrl = url.ifBlank { homeUrl() }
        // 当前标签先隐藏，新标签有自己的 WebView
        webView?.visibility = View.GONE
        val tab = BrowserTabs.Tab("新标签页", targetUrl, fromPopup = fromPopup).apply {
            session.navigate(targetUrl)
        }
        BrowserTabs.tabs.add(tab)
        BrowserTabs.activeIndex = BrowserTabs.tabs.lastIndex
        val wv = createWebView()
        tab.webView = wv
        showOnlyWebView(wv)
        webView = wv
        binding.tabs.text = BrowserTabs.tabs.size.toString()
        BrowserTabs.persist()
        wv.currentHost = targetUrl
        wv.loadUrl(targetUrl)
        updateForwardButton()
    }

    private fun switchTab(index: Int) {
        if (index !in BrowserTabs.tabs.indices || index == BrowserTabs.activeIndex) return
        captureThumb(BrowserTabs.tabs[BrowserTabs.activeIndex])
        webView?.visibility = View.GONE
        BrowserTabs.activeIndex = index
        val tab = BrowserTabs.tabs[index]
        tab.lastUsedAt = System.currentTimeMillis()
        val wv = tab.webView?.takeIf { !it.isDestroyed }
        if (wv != null) {
            // 已有 WebView：直接显示，保留滚动位置/历史/JS 状态
            showOnlyWebView(wv)
            webView = wv
            binding.tvUrl.text = displayUrl(wv.url ?: tab.url)
            updateForwardButton()
        } else {
            val created = createWebView()
            tab.webView = created
            showOnlyWebView(created)
            webView = created
            val state = tab.state
            tab.state = null
            if (state != null) {
                created.restoreState(state)
            } else {
                created.currentHost = tab.url
                created.loadUrl(if (tab.url.isBlank()) "about:blank" else tab.url)
            }
            updateForwardButton()
        }
        binding.tabs.text = BrowserTabs.tabs.size.toString()
        BrowserTabs.persist()
    }

    private fun closeTab(index: Int) {
        if (index !in BrowserTabs.tabs.indices) return
        // 关闭的是最后一个：销毁后自动开一个全新标签页
        if (BrowserTabs.tabs.size <= 1) {
            BrowserTabs.tabs.forEach { tab ->
                deleteThumb(tab)
                tab.webView?.let {
                    binding.webViewContainer.removeView(it)
                    it.destroy()
                }
            }
            BrowserTabs.tabs.clear()
            BrowserTabs.activeIndex = 0
            webView = null
            newTab()
            return
        }
        val tab = BrowserTabs.tabs.removeAt(index)
        deleteThumb(tab)
        tab.webView?.let {
            binding.webViewContainer.removeView(it)
            it.destroy()
        }
        if (index < BrowserTabs.activeIndex) {
            BrowserTabs.activeIndex--
        } else if (index == BrowserTabs.activeIndex) {
            BrowserTabs.activeIndex = BrowserTabs.activeIndex.coerceAtMost(BrowserTabs.tabs.lastIndex)
            val next = BrowserTabs.tabs[BrowserTabs.activeIndex]
            val wv = next.webView?.takeIf { !it.isDestroyed } ?: createWebView().also {
                next.webView = it
                it.currentHost = next.url
                it.loadUrl(next.url)
            }
            showOnlyWebView(wv)
            webView = wv
            binding.tvUrl.text = displayUrl(wv.url ?: next.url)
            updateForwardButton()
        }
        binding.tabs.text = BrowserTabs.tabs.size.toString()
    }

    /** Edge 风格网格菜单 */
    private fun showBrowserMenu() {
        val b = DialogBrowserMenuBinding.inflate(LayoutInflater.from(context))
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(b.root)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        dialog.setOnShowListener {
            applySheetNavBar(dialog)
            dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
        b.menuBookmarks.click {
            dialog.dismiss()
            openRecords(BrowserRecordsActivity.BOOKMARKS)
        }
        b.menuHistory.click {
            dialog.dismiss()
            openRecords(BrowserRecordsActivity.HISTORY)
        }
        b.menuRead.click {
            dialog.dismiss()
            browserActions.openReadMode()
        }
        val nightNow = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        b.tvNightLabel.text = if (nightNow) "日间模式" else "夜间模式"
        b.ivNightIcon.setImageResource(
            if (nightNow) R.drawable.ic_menu_day else R.drawable.ic_menu_night
        )
        b.ivNightIcon.setColorFilter(
            Color.parseColor(if (nightNow) "#F9AB00" else "#3949AB")
        )
        b.menuNightMode.click {
            dialog.dismiss()
            val night = (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            val target = if (night) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            appEssentials.appDayNightMode = target
            AppCompatDelegate.setDefaultNightMode(target)
        }
        b.menuExit.click {
            // 主动退出：保留标签和当前页，下次进入恢复
            BrowserTabs.persist()
            BrowserSession.saveResume(
                if (webView?.canGoBack() == true) webView?.url else null
            )
            // 等面板完全关闭后再退出，避免点击事件穿透到底部按钮
            dialog.setOnDismissListener {
                val exit = onExitRequest
                if (exit != null) {
                    exit.invoke()
                } else {
                    val act = activity ?: (context as? android.app.Activity)
                    Log.i("menuExit finish activity=$act")
                    act?.finish()
                }
            }
            dialog.dismiss()
        }
        b.menuAdBlock.click {
            dialog.dismiss()
            context.startActivity(Intent(context, BrowserAdBlockActivity::class.java))
        }
        b.menuFontValue.text = globalConfig.webFontZoomSize.toString()
        b.menuFontAdd.click {
            val wv = webView ?: return@click
            wv.settings.textZoom = globalConfig.webFontZoomSize + 4
            globalConfig.webFontZoomSize = wv.settings.textZoom
            b.menuFontValue.text = globalConfig.webFontZoomSize.toString()
        }
        b.menuFontSubtract.click {
            val wv = webView ?: return@click
            wv.settings.textZoom = globalConfig.webFontZoomSize - 4
            globalConfig.webFontZoomSize = wv.settings.textZoom
            b.menuFontValue.text = globalConfig.webFontZoomSize.toString()
        }
        when (globalConfig.crossOriginMode) {
            1 -> b.menuRbAllow.isChecked = true
            2 -> b.menuRbDeny.isChecked = true
            else -> b.menuRbAsk.isChecked = true
        }
        b.menuCrossOrigin.setOnCheckedChangeListener { _, checkedId ->
            crossOriginOverride = -1
            globalConfig.crossOriginMode = when (checkedId) {
                R.id.menu_rb_allow -> 1
                R.id.menu_rb_deny -> 2
                else -> 0
            }
        }
        b.menuDesktop.click {
            val wv = webView
            if (wv?.url.isNullOrBlank()) {
                toast("无可用页面")
                return@click
            }
            globalConfig.webUaMode = if (globalConfig.webUaMode == 1) 0 else 1
            wv.settings.userAgentString = webUserAgent()
            wv.clearCache(true)
            wv.reload()
            dialog.dismiss()
        }
        b.menuShelf.click {
            dialog.dismiss()
            browserActions.addToShelf()
        }
        dialog.show()
    }

    private fun openRecords(mode: String) {
        val wv = webView ?: return
        context.startActivity(Intent(context, BrowserRecordsActivity::class.java).apply {
            putExtra(BrowserRecordsActivity.MODE, mode)
            putExtra(BrowserRecordsActivity.CURRENT_TITLE, wv.title.orEmpty())
            putExtra(BrowserRecordsActivity.CURRENT_URL, wv.url.orEmpty())
        })
    }

    /** 输入内容：网址直接打开，否则用当前搜索引擎搜索 */
    private fun resolveAddress(input: String): String {
        val text = input.trim()
        if (isUrlInput(text)) {
            return if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
        }
        val index = globalConfig.webSearchEngine.coerceIn(SEARCH_ENGINES.indices)
        return SEARCH_ENGINES[index].second.format(URLEncoder.encode(text, "UTF-8"))
    }

    /** 判断输入是否应作为网址处理（与 resolveAddress 的判定保持一致，供前导图标切换使用） */
    private fun isUrlInput(text: String): Boolean {
        if (text.startsWith("http://") || text.startsWith("https://")) return true
        return text.contains(".") && !text.contains(" ") && !text.contains("。")
    }

    private fun displayUrl(url: String?): String {
        if (url.isNullOrBlank()) return "搜索或输入网址"
        return url.removePrefix("https://").removePrefix("http://").trimEnd('/')
    }

    private fun updateForwardButton() {
        val wv = webView
        val tab = BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)
        // 原生历史没有时，标签保存的 URL 历史里还有下一页，前进按钮也应可用
        val virtualForward = tab?.session?.canForward == true
        val nativeForward = wv != null && wv.canGoForward() && wv.meaningfulForwardStep() > 0
        binding.forward.isEnabled = nativeForward || virtualForward
        binding.refresh.isSelected = (wv?.progress ?: 100) < 100
        // 退到底（再返回就退出浏览器）时，返回箭头切换为叉叉关闭
        val canBack = tab?.session?.canBack == true
        if (canBack) {
            binding.backward.setImageResource(R.drawable.browser_navigation_forward)
            binding.backward.imageTintList = null
            binding.backward.scaleX = -1f
        } else {
            binding.backward.setImageResource(R.mipmap.icon_exit)
            binding.backward.imageTintList = null
            binding.backward.scaleX = 1f
        }
    }

    /** 返回向后最近的可用页面步数（负值），没有则返回 0。 */
    private fun MyWebView.meaningfulBackStep(): Int {
        val list = copyBackForwardList()
        pruneSkippedHistoryIndices(list.size)
        val current = list.currentItem?.url?.substringBefore('#')?.trimEnd('/')
        var idx = list.currentIndex - 1
        var step = -1
        while (idx >= 0) {
            val u = list.getItemAtIndex(idx).url
            val samePage = current != null && u != null &&
                    u.substringBefore('#').trimEnd('/') == current
            // 跳过重定向产生的"同一个 URL"历史，否则返回会一直重新加载首页
            if (!samePage && !isSkippableHistoryItem(idx, u)) return step
            idx--
            step--
        }
        return 0
    }

    /** 返回向前最近的可用页面步数（正值），没有则返回 0。 */
    private fun MyWebView.meaningfulForwardStep(): Int {
        val list = copyBackForwardList()
        pruneSkippedHistoryIndices(list.size)
        var idx = list.currentIndex + 1
        var step = 1
        while (idx < list.size) {
            val u = list.getItemAtIndex(idx).url
            if (!isSkippableHistoryItem(idx, u)) return step
            idx++
            step++
        }
        return 0
    }

    private fun isSkippableHistoryItem(index: Int, url: String?): Boolean {
        if (url.isNullOrBlank() || url == "about:blank") return true
        // 索引可能因新导航被截断/复用，需同时校验 url 一致才认为是被标记跳过的条目
        return skippedHistoryIndices[index] == url
    }

    private fun pruneSkippedHistoryIndices(historySize: Int) {
        val it = skippedHistoryIndices.entries.iterator()
        while (it.hasNext()) {
            if (it.next().key >= historySize) it.remove()
        }
    }

    private fun MyWebView.markCurrentHistoryAsSkipped() {
        val list = copyBackForwardList()
        val idx = list.currentIndex
        if (idx in 0 until list.size) {
            skippedHistoryIndices[idx] = list.getItemAtIndex(idx).url
        }
    }

    private fun createWebViewClient() = object : MyWebViewClient(
        lifecycleScope!!,
        { url, request ->
            Log.i("onAllow isRedirect:${request?.isRedirect} hasGesture:${request?.hasGesture()} isForMainFrame:${request?.isForMainFrame} $url")
            // 自动重定向（主框架、无用户手势）：标记当前页（重定向源）为跳过，
            // 后退时越过它，避免 后退→重定向源→再次重定向 的死循环。
            // 用户点击（hasGesture）不标记，保留可后退到来源页。
            if (request?.isForMainFrame == true && request?.hasGesture() == false) {
                webView?.markCurrentHistoryAsSkipped()
            }
            loadUrl(url)
        },
        { url, request ->
            Log.i("onBlock isRedirect:${request?.isRedirect} hasGesture:${request?.hasGesture()} isForMainFrame:${request?.isForMainFrame} $url")
            if (request?.isForMainFrame == true && !request.hasGesture()) {
                recoverFromBlockedMainFrame(url)
            }
        },
        { effectiveCrossOriginMode() }
    ) {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
            if (request.isForMainFrame) {
                if (request.hasGesture()) {
                    // 用户点击链接：开启一次新导航事务，压栈在 onPageFinished 提交时生效
                    val tab = BrowserTabs.tabs.firstOrNull { it.webView === view }
                    if (tab != null) {
                        tab.session.rollback() // 丢弃上一笔未提交的预演
                        tab.session.beginNavigate(request.url.toString())
                    }
                    pendingMainFrameRedirect = false
                } else {
                    // 无用户手势的主框架导航（JS 跳转等）视为重定向：不产生额外历史
                    pendingMainFrameRedirect = true
                }
            }
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
            if (request.isForMainFrame && request.isRedirect) {
                // HTTP 重定向：重定向源不压栈
                pendingMainFrameRedirect = true
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            if (url != null) {
                binding.tvUrl.text = displayUrl(url)
            }
        }

        override fun onPageFinished(webView: WebView?, url: String?) {
            super.onPageFinished(webView, url)
            binding.searchRefresh.visibility = GONE
            val wv = webView as? MyWebView
            if (wv != null) {
                val target = wv.forceReloadTargetUrl
                if (target != null) {
                    // 强制刷新第一步：空白页加载完成，加载目标 URL（全新导航，绕过 BFCache）
                    wv.markCurrentHistoryAsSkipped() // about:blank
                    wv.loadUrl(target)
                    wv.forceReloadTargetUrl = null
                    return
                }
            }
            binding.swipeRefresh.isRefreshing = false
            binding.tvUrl.text = displayUrl(url)
            val tabForHistory = BrowserTabs.tabs.firstOrNull { it.webView === webView }
            tabForHistory?.apply {
                title = webView?.title.orEmpty()
                if (!url.isNullOrBlank()) {
                    this.url = url
                    session.title = title
                    if (session.hasPendingNav) {
                        // 事务提交：匹配（含重定向放宽）才提交，否则视为过期回调丢弃
                        if (session.isCurrentNav(url, pendingMainFrameRedirect)) {
                            session.commit(url)
                            pendingMainFrameRedirect = false
                        }
                    } else if (pendingMainFrameRedirect) {
                        // 无事务的重定向到达：URL1 出栈（不压栈），URL2 成为当前页
                        session.currentUrl = url
                        pendingMainFrameRedirect = false
                    } else if (!session.samePage(session.currentUrl, url)) {
                        // 无事务的普通前进导航（兜底）：压入后退栈并清空前进栈
                        session.navigate(url)
                    }
                }
            }
            // 后退/前进或页面跳转后，立即把当前 URL 落盘，退出恢复时停在最后一页
            BrowserTabs.persist()
            binding.tabs.text = BrowserTabs.tabs.size.toString()
                updateForwardButton()
            if (virtualNavigating) {
                // 虚拟回退/前进加载完后清掉新产生的 WebView 历史，
                // 否则再按后退会在这两个页面之间来回跳
                webView?.clearHistory()
                virtualNavigating = false
                updateForwardButton()
            }

            Log.i("onPageFinished ${webView?.currentHost} title:${webView?.title}")
            val list = history.value!!.toMutableList()
            webView?.url?.let { url ->
                val now = System.currentTimeMillis()
                // 当天去重：同一天内同一地址只保留最新一条；跨天保留历史（昨天/前天各留一条）
                list.removeAll { it.url == url && isSameDay(it.time, now) }
                list.add(HistoryItem(webView.title ?: "无标题", url, now))
                list.sortDescending()
                // 超过上限只丢弃最旧的一条（标记项排在前面，不会被优先丢弃）
                if (list.size > MAX_HISTORY) {
                    list.removeAt(list.lastIndex)
                }
                History.list = list
                history.postValue(list)
            }
        }
    }

    private fun recoverFromBlockedMainFrame(blockedUrl: String) {
        val wv = webView ?: return
        wv.post {
            if (webView !== wv || wv.isDestroyed || wv.isBlank != true) return@post
            wv.markCurrentHistoryAsSkipped()
            val step = wv.meaningfulBackStep()
            if (step < 0) {
                wv.goBackOrForward(step)
                updateForwardButton()
                Log.i("recoverFromBlockedMainFrame step:$step blocked:$blockedUrl")
            }
        }
    }

    private fun createWebChromeClient() = object : WebChromeClient() {
        /** 网页里 target=_blank / window.open：在新标签页打开，而不是丢弃 */
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean {
            // 统一禁止网页自动创建新标签：直接忽略 window.open / target=_blank
            return false
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (view !== webView) return
            binding.searchRefresh.progress = newProgress
            if (newProgress == 100) {
                binding.refresh.isSelected = false
                binding.searchRefresh.visibility = GONE
            } else {
                binding.refresh.isSelected = true
                binding.searchRefresh.visibility = VISIBLE
            }
        }
    }

    private fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val offset = (imeInsets.bottom - systemBars.bottom).coerceAtLeast(0)
            toolbarImeOffset = -offset.toFloat()
            if (offset > 0) showToolbar()
            applyToolbarTranslation()
            insets
        }

        // 首次布局完成后，按工具栏实际高度给网页留出底部空间
        binding.browserBookCityToolbar.post {
            if (!toolbarHidden) setContentInsetForToolbar(true)
        }

        binding.refresh.click {
            val wv = webView ?: return@click
            if (binding.refresh.isSelected) {
                stopLoading()
                binding.refresh.isSelected = false
                return@click
            }
            val url = wv.url ?: return@click
            if (url.isBlank() || url == "about:blank") return@click

            // 直接 reload 在渲染状态损坏时不会重绘（一直白屏）；
            // 走"先 about:blank 再加载目标"，强制重建文档，刷新才能恢复画面
            forceReload(showToast = false)
        }
        binding.refresh.setOnLongClickListener {
            forceReload()
            true
        }

        // 下拉刷新页面：仅当当前 WebView 滚到顶部时触发
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            webView?.canScrollVertically(-1) == true
        }
        binding.swipeRefresh.setOnRefreshListener {
            val wv = webView
            if (wv == null || wv.url.isNullOrBlank() || wv.url == "about:blank") {
                binding.swipeRefresh.isRefreshing = false
            } else {
                forceReload(showToast = false)
            }
        }

        // 后退按钮：返回上一页（历史回退），退出浏览器用系统返回键
        binding.backward.click {
            if (!back()) {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
        }
        // 地址栏：弹出地址面板（标题可复制、分享、切换 UA、编辑网址）
        binding.tvUrl.click { showAddressSheet() }
        binding.forward.click {
            goForward()
        }
        binding.settings.setOnClickListener { showBrowserMenu() }
        // 标签按钮：点击区域与其它按钮一致（整块 weight 区域 + 水波纹反馈）
        binding.tabsButton.click { openTabsManager() }
        binding.home.click {
            Log.i("回到首页")
            val home = webBook?.url ?: globalConfig.bookCityUrl
            loadUrl(home)
        }
        // 长按主页：把当前页设为书城/新窗口的默认主页
        binding.home.setOnLongClickListener {
            val current = webView?.url
            if (current.isNullOrBlank() || !current.startsWith("http")) {
                toast("当前页面无法设为主页")
            } else {
                globalConfig.bookCityUrl = current
                toast("已设为主页")
            }
            true
        }
    }

    fun forceReload(showToast: Boolean = true) {
        val wv = webView ?: run {
            if (showToast) toast("无可刷新的页面")
            return
        }
        val url = wv.url
        if (url.isNullOrBlank() || url == "about:blank") {
            if (showToast) toast("无可刷新的页面")
            return
        }
        BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)?.session?.rollback() // 清理残留事务
        wv.markCurrentHistoryAsSkipped() // 强制刷新的当前损坏页
        wv.forceReloadTargetUrl = url
        wv.loadUrl("about:blank") // 先加载空白页，丢弃损坏的页面/渲染状态
        if (showToast) toast("强制刷新")
        Log.i("forceReload $url")
    }

}


object History {
    var list: List<HistoryItem>
        get() = runBlocking(Dispatchers.IO) {
            DbFactory.db.browserDataDao().getAllHistory().map { HistoryItem(it.title, it.url, it.time) }
        }
        set(value) = runBlocking(Dispatchers.IO) {
            DbFactory.db.browserDataDao().replaceAllHistory(
                value.map { BrowserHistoryEntity(0, it.title, it.url, it.time) }
            )
        }
}

/** 两个时间戳是否属于同一天（用于历史记录"当天去重、不跨天去重"） */
private fun isSameDay(a: Long, b: Long): Boolean {
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
            java.time.Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
}

class HistoryItem(var title: String, val url: String, val time: Long = System.currentTimeMillis()) : Comparable<HistoryItem> {
    override fun compareTo(other: HistoryItem): Int = time.compareTo(other.time)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HistoryItem
        if (time != other.time) return false
        if (title != other.title) return false
        if (url != other.url) return false
        return true
    }

    override fun hashCode(): Int {
        var result = time.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + url.hashCode()
        return result
    }

    override fun toString(): String {
        return "HistoryItem(title='$title', time=$time, url='$url')"
    }
}
