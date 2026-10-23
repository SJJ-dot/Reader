package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
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
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.init
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.copyToClipboard
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.hideKeyboard
import com.sjianjun.reader.utils.showKeyboard
import com.sjianjun.reader.utils.webUserAgent
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import com.tencent.mmkv.MMKV
import com.sjianjun.reader.utils.activity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

/*
 * Created by sjj on 2020-07-10
 */
class CustomWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    internal var lifecycleScope: LifecycleCoroutineScope? = null
    private var updateWebBookJob: Job? = null
    var webBook: WebBook? = null
    private var vm: BookCityViewModel? = null
    val binding: CustomWebViewBinding = CustomWebViewBinding.inflate(LayoutInflater.from(context), this, true)
    var adBlock: AdBlock? = null
    val history = MutableLiveData<List<HistoryItem>>()

    /** 单 WebView 实例：所有导航复用它，后退/前进依赖 WebView 原生历史 */
    var webView: MyWebView? = null
        private set
    private val skippedHistoryIndices = linkedMapOf<Int, String>()

    /** 当前主框架导航是否通过重定向/无手势跳转到达（重定向源不压栈） */
    private var pendingMainFrameRedirect = false

    /** 底部工具栏是否已收起（页面上滑时收起、下滑时显示） */
    private var toolbarHidden = false

    private companion object {
        const val MAX_TABS = 50
        /** 内存中最多同时保留的 WebView 实例数，其余标签只保留 URL/历史 */
        const val MAX_LIVE_TABS = 5
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

    /** 通过标签自带的 URL 历史回退/前进，避免把回退页再次记入历史 */
    private var virtualNavigating = false

    /** 原生 goBack/goForward 后 onPageFinished 不要再追加历史 */
    private var suppressHistoryRecord = false

    /** 防止广告/重定向导致回退停留在同一 URL 无限循环 */
    private var lastBackUrl: String? = null
    private var lastBackTime = 0L
    private var backRepeatCount = 0

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

    fun effectiveCrossOriginMode(): Int {
        return if (crossOriginOverride >= 0) crossOriginOverride else globalConfig.crossOriginMode
    }

    fun init(owner: LifecycleOwner, adBlock: AdBlock, webBook: WebBook?, vm: BookCityViewModel) {
        this.webBook = webBook
        this.adBlock = adBlock
        this.vm = vm

        initView()
        history.value = History.list.toMutableList()
        this.lifecycleScope = owner.lifecycleScope
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
        tab.session.navigate(url)
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

    /** 切到后台：保存每个标签的 WebView 状态并销毁，释放内存 */
    fun onBackground() {
        BrowserTabs.tabs.forEach { tab ->
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
        webView = null
        BrowserTabs.persist()
    }

    /** 回到前台：只恢复当前标签，其余按需恢复 */
    fun onForeground() {
        val tab = BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex) ?: return
        if (tab.webView?.isDestroyed == false) return
        val wv = createWebView()
        tab.webView = wv
        showOnlyWebView(wv)
        webView = wv
        val state = tab.state
        tab.state = null
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

    /** 内存里最多保留 MAX_LIVE_TABS 个 WebView，超出按创建顺序销毁最旧的（列表/历史仍保留） */
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
    private fun attachToolbarAutoHide(wv: WebView) {
        wv.setOnTouchListener { _, event ->
            // 设置面板展开时不联动，避免把输入框拖走
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    binding.browserBookCityToolbar.animate().cancel()
                    toolbarTouchStartY = event.rawY
                    toolbarTouchLastY = event.rawY
                    toolbarDragging = false
                    toolbarTracking = true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!toolbarTracking) return@setOnTouchListener false
                    if (!toolbarDragging) {
                        // 超过触摸阈值才开始跟手，避免轻点/抖动导致工具栏闪动
                        if (abs(event.rawY - toolbarTouchStartY) <= toolbarTouchSlop) {
                            return@setOnTouchListener false
                        }
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
            false
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
        val container = binding.webViewContainer
        val lp = container.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val target = px.coerceIn(0, toolbarHeightPx().toInt())
        if (lp.bottomMargin == target) return
        lp.bottomMargin = target
        container.layoutParams = lp
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

    private fun samePage(a: String?, b: String?): Boolean =
        a != null && b != null &&
                a.substringBefore('#').trimEnd('/') == b.substringBefore('#').trimEnd('/')

    /** 记录标签的 URL 历史（跨进程退出时用于回退） */
    private fun recordHistory(tab: BrowserTabs.Tab, url: String?) {
        if (url.isNullOrBlank() || url == "about:blank") return
        val index = tab.historyIndex
        if (index in tab.history.indices && tab.history[index] == url) return
        while (tab.history.size > index + 1) {
            tab.history.removeAt(tab.history.lastIndex)
        }
        tab.history.add(url)
        tab.historyIndex = tab.history.lastIndex
        if (tab.history.size > 50) {
            tab.history.removeAt(0)
            tab.historyIndex = (tab.historyIndex - 1).coerceAtLeast(0)
        }
        BrowserTabs.persist()
    }

    fun back(): Boolean {
        val wv = webView ?: return false
        val tab = BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)
        val target = tab?.session?.back()
        if (target == null) {
            // 后退到底：清空当前标签页会话（重置回主页），退出浏览器。
            // 标签页本身不删除——只有用户手动才能新建/删除标签页，下次进入重建该标签页。
            val cur = BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)
            if (cur != null) {
                deleteThumb(cur)
                cur.session.backStack.clear()
                cur.session.forwardStack.clear()
                cur.url = homeUrl()
                cur.session.currentUrl = homeUrl()
                cur.webView?.let {
                    binding.webViewContainer.removeView(it)
                    it.destroy()
                }
                cur.webView = null
            }
            webView = null
            BrowserSession.clear()
            return false
        }
        tab.url = target
        val step = if (wv.canGoBack()) wv.meaningfulBackStep() else 0
        if (step < 0) {
            suppressHistoryRecord = true
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
        val tab = BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)
        val target = tab?.session?.forward() ?: return false
        tab.url = target
        val step = if (wv.canGoForward()) wv.meaningfulForwardStep() else 0
        if (step > 0) {
            suppressHistoryRecord = true
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
    private fun showAddressSheet() {
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
        // 搜索图标：切换搜索引擎
        b.btnSearchEngine.click {
            val names = SEARCH_ENGINES.map { it.first }.toTypedArray()
            MaterialAlertDialogBuilder(context)
                .setTitle("搜索引擎")
                .setSingleChoiceItems(names, globalConfig.webSearchEngine) { d, which ->
                    globalConfig.webSearchEngine = which
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

    /** 底部面板打开时：导航栏与面板同色（日间白/夜间深色），图标对比度跟随 */
    private fun applySheetNavBar(dialog: BottomSheetDialog) {
        val window = dialog.window ?: return
        val night = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val color = ContextCompat.getColor(context, R.color.dn_background_card)
        window.navigationBarColor = color
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightNavigationBars = !night
        // Android 15+ 忽略 navigationBarColor：在导航栏区域盖同色 scrim
        val decor = window.decorView as? android.view.ViewGroup ?: return
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

    /** 输入时用历史记录做候选，末尾附一条搜索入口 */
    private fun renderAddressSuggestions(
        b: DialogBrowserAddressBinding,
        dialog: BottomSheetDialog,
        query: String,
    ) {
        val container = b.suggestionsList
        container.removeAllViews()
        val q = query.trim()
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
        context.startActivity(Intent(context, BrowserTabsActivity::class.java))
    }

    /** 在新标签页打开指定 URL（供收藏夹/历史记录长按调用） */
    fun openInNewTab(url: String) {
        newTab(url)
    }

    private fun homeUrl(): String = webBook?.url ?: globalConfig.bookCityUrl

    private fun newTab(url: String = "", fromPopup: Boolean = false) {
        if (BrowserTabs.tabs.size >= MAX_TABS) {
            // 达到上限：回收最久未使用（非当前）的标签，信息已随列表落盘
            val recycleIndex = if (BrowserTabs.activeIndex == 0) 1 else 0
            val old = BrowserTabs.tabs.removeAt(recycleIndex)
            deleteThumb(old)
            old.webView?.let {
                binding.webViewContainer.removeView(it)
                it.destroy()
            }
            if (recycleIndex < BrowserTabs.activeIndex) BrowserTabs.activeIndex--
        }
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
            globalConfig.appDayNightMode = target
            AppCompatDelegate.setDefaultNightMode(target)
        }
        b.menuExit.click {
            // 主动退出：保留标签和当前页，下次进入恢复
            BrowserTabs.persist()
            BrowserSession.saveResume(
                if (webView?.canGoBack() == true) webView?.url else null
            )
            // 等面板完全关闭后再 finish，避免点击事件穿透到底部按钮
            dialog.setOnDismissListener {
                val act = activity ?: (context as? android.app.Activity)
                Log.i("menuExit finish activity=$act")
                act?.finish()
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
        b.menuHome.click {
            dialog.dismiss()
            binding.home.performClick()
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

    /** 把当前页加入收藏夹（即主页列表） */
    private fun addCurrentToFavorites() {
        val url = webView?.url ?: return
        if (!url.startsWith("http")) {
            toast("当前页面无法收藏")
            return
        }
        BookmarkStore.add(webView?.title.orEmpty(), url)
        toast("已加入收藏夹")
    }

    private fun openRecords(mode: String) {
        val wv = webView ?: return
        context.startActivity(Intent(context, BrowserRecordsActivity::class.java).apply {
            putExtra(BrowserRecordsActivity.MODE, mode)
            putExtra(BrowserRecordsActivity.CURRENT_TITLE, wv.title.orEmpty())
            putExtra(BrowserRecordsActivity.CURRENT_URL, wv.url.orEmpty())
        })
    }

    /** 收藏夹：收藏当前页 / 打开已收藏的网页 */
    internal fun showBookmarks() {
        val bookmarks = BookmarkStore.list()
        val items = mutableListOf("收藏当前页")
        items += bookmarks.map { "${it.title}\n${it.url}" }
        MaterialAlertDialogBuilder(context)
            .setTitle("收藏夹")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    val wv = webView ?: return@setItems
                    BookmarkStore.add(wv.title ?: "", wv.url ?: "")
                    toast("已收藏")
                } else {
                    loadUrl(bookmarks[which - 1].url)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    /** 历史记录：按时间倒序，点击直接打开 */
    internal fun showHistory() {
        val history = History.list.sortedByDescending { it.time }
        if (history.isEmpty()) {
            toast("暂无历史记录")
            return
        }
        val items = history.map { it.title.ifBlank { it.url } }
        MaterialAlertDialogBuilder(context)
            .setTitle("历史记录")
            .setItems(items.toTypedArray()) { _, which -> loadUrl(history[which].url) }
            .setNeutralButton("清空") { _, _ ->
                History.list = emptyList()
                toast("已清空")
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    /** 输入内容：网址直接打开，否则用当前搜索引擎搜索 */
    private fun resolveAddress(input: String): String {
        val text = input.trim()
        if (text.startsWith("http://") || text.startsWith("https://")) return text
        val looksLikeUrl = text.contains(".") && !text.contains(" ") && !text.contains("。")
        if (looksLikeUrl) return "https://$text"
        val index = globalConfig.webSearchEngine.coerceIn(SEARCH_ENGINES.indices)
        return SEARCH_ENGINES[index].second.format(URLEncoder.encode(text, "UTF-8"))
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

    // 抽取更新 webBook 的逻辑：在同一主域下更新 lastUrl/lastTitle/updateTime 并持久化
    private fun updateWebBookIfSameDomain(view: WebView?, url: String?, updateTitle: Boolean) {
        if (url == null) return
        val book = webBook ?: return
        val view = view as? MyWebView ?: return
        updateWebBookJob?.cancel()
        updateWebBookJob = lifecycleScope?.launch {
            val urlHost = url.toHttpUrlOrNull()?.topPrivateDomain()
            if (book.url.toHttpUrlOrNull()?.topPrivateDomain() == urlHost) {
                if (book.lastUrl != url) {
                    book.lastUrl = url
                    book.offest = 0
                    book.scrollOffset = 0
                    book.updateTime = System.currentTimeMillis()
                }
                if (updateTitle) {
                    book.lastTitle = view.getTitleFromMeta().ifBlank { view.title ?: "无标题" }
                } else {
                    view.post {
                        if (book.lastUrl == url) {
                            updateWebBookIfSameDomain(view, url, true)
                        }
                    }
                }
                vm?.insertWebBook(book)
            }
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
                // 无用户手势的主框架导航（JS 跳转等）视为重定向：重定向源不压栈
                pendingMainFrameRedirect = !request.hasGesture()
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
                updateWebBookIfSameDomain(view, url, updateTitle = false)
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
            // 页面加载完成后再更新 title（如果可用）并持久化
            updateWebBookIfSameDomain(webView, url, updateTitle = true)
            binding.tvUrl.text = displayUrl(url)
            val tabForHistory = BrowserTabs.tabs.firstOrNull { it.webView === webView }
            if (suppressHistoryRecord) {
                suppressHistoryRecord = false
            }
            tabForHistory?.apply {
                title = webView?.title.orEmpty()
                if (!url.isNullOrBlank()) {
                    this.url = url
                    session.title = title
                    if (pendingMainFrameRedirect) {
                        // 重定向到达：URL1 出栈（不压栈），URL2 成为当前页
                        session.currentUrl = url
                        pendingMainFrameRedirect = false
                    } else if (samePage(session.currentUrl, url)) {
                        // 后退/前进/刷新到达的页面：session 栈已由 back()/forward() 维护，
                        // 只同步当前页；不能 navigate，否则 forwardStack.clear() 会清掉前进栈
                        session.currentUrl = url
                    } else {
                        // 普通前进导航（点击链接等）：压入后退栈并清空前进栈
                        session.navigate(url)
                    }
                }
            }
            // 后退/前进或页面跳转后，立即把当前 URL 落盘，退出恢复时停在最后一页
            BrowserTabs.persist()
            // 首次进入页面时清掉重定向产生的历史，
            // 否则在首页按返回会沿着重定向链反复重新加载首页
            tabForHistory?.let { tab ->
                if (!tab.entryCleared && !tab.restored) {
                    binding.root.postDelayed({
                        if (tab.webView === webView) {
                            val currentHost = runCatching {
                                java.net.URI(webView?.url.orEmpty()).host
                            }.getOrNull()
                            val entryHost = runCatching {
                                java.net.URI(tab.initialUrl.orEmpty()).host
                            }.getOrNull()
                            // 还在入口站点的重定向链里才清历史；已经点进别的页面就不清
                            if (currentHost != null && currentHost == entryHost) {
                                webView?.clearHistory()
                            }
                            tab.entryCleared = true
                        }
                    }, 1_000)
                }
            }
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
                val item = HistoryItem(webView.title ?: "无标题", url)
                item.isMark = list.firstOrNull { it.url == url }?.isMark ?: false
                list.removeAll { item -> item.url == url }
                list.add(item)
                list.sortDescending()
                if (list.size > 100) {
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
                wv.stopLoading()
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
        binding.tabs.click { openTabsManager() }
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

    fun clearHistory() {
        History.list = mutableListOf()
        history.postValue(mutableListOf())
    }

    fun removeHistory(item: HistoryItem) {
        val list = history.value!!.toMutableList()
        list.remove(item)
        History.list = list
        history.postValue(list)
    }

    fun markHistory(item: HistoryItem) {
        item.isMark = !item.isMark
        val list = history.value!!.toMutableList()
        list.remove(item)
        list.add(0, item)
        list.sortDescending()
        History.list = list
        history.postValue(list)
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
        wv.markCurrentHistoryAsSkipped() // 强制刷新的当前损坏页
        wv.forceReloadTargetUrl = url
        wv.loadUrl("about:blank") // 先加载空白页，丢弃损坏的页面/渲染状态
        if (showToast) toast("强制刷新")
        Log.i("forceReload $url")
    }

}


object History : DelegateSharedPref(MMKV.mmkvWithID("AppConfig_History")) {
    var list by dataPref("History", listOf<HistoryItem>())
}

class HistoryItem(var title: String, val url: String, val time: Long = System.currentTimeMillis(), var isMark: Boolean = false) : Comparable<HistoryItem> {
    override fun compareTo(other: HistoryItem): Int {
        if (isMark != other.isMark) return if (isMark) 1 else -1
        return time.compareTo(other.time)
    }

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
        return "HistoryItem(title='$title', isMark=$isMark, time=$time, url='$url')"
    }
}
