package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.LruCache
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.URL
import com.sjianjun.reader.WEB_ID
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.databinding.ActivityBrowserReadModeBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.event.observe
import com.sjianjun.reader.module.reader.ReaderClickAreaAction
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.ReplacementRuleUseCase
import com.sjianjun.reader.utils.ActivityManger
import com.sjianjun.reader.utils.DiskCacheUtil
import com.sjianjun.reader.utils.TtsService
import com.sjianjun.reader.utils.gone
import com.sjianjun.reader.utils.init
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import sjj.alog.Log
import sjj.novel.view.reader.bean.BookRecordBean
import sjj.novel.view.reader.page.ChapterPageCache
import sjj.novel.view.reader.page.DisplayParams
import sjj.novel.view.reader.page.PageLoader
import sjj.novel.view.reader.page.PageLoader.Companion.STATUS_LOADING
import sjj.novel.view.reader.page.PageMode
import sjj.novel.view.reader.page.PageStyle
import sjj.novel.view.reader.page.PageView
import sjj.novel.view.reader.page.TxtChapter
import java.io.File
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.view.isVisible
import com.sjianjun.reader.view.click

class BrowserReadModeActivity : BaseActivity() {

    private val startUrl by lazy { intent.getStringExtra(URL) ?: "" }
    private val webBookId by lazy { intent.getStringExtra(WEB_ID) ?: "" }

    var webBook: WebBook? = null
    private var needRestore = true

    private lateinit var binding: ActivityBrowserReadModeBinding
    private var chapterFragment: ChapterListDialogFragment? = null

    /** 广告/跨域拦截器，与浏览器模式共享同一份配置 */
    val adBlock: AdBlock = AdBlock

    private var extractJob: Job? = null
    private val loadUrlQueue = ArrayDeque<LoadUrlRequest>()
    private var loadUrlQueueJob: Job? = null

    private var updateWebBookJob: Job? = null

    /** 阅读模式本次打开后的内存历史记录，不落盘。 */
    val readModeHistory = mutableListOf<HistoryItem>()
    private val forwardHistoryStack = ArrayDeque<HistoryItem>()
    private var isHistoryNavigation = false

    private val webDao get() = com.sjianjun.reader.repository.DbFactory.db.webBookDao()

    val pageLoader get() = binding.readModePageView.pageLoader!!

    /** 后台 WebView 是否正在加载。供设置面板的刷新按钮订阅。 */
    val isHiddenLoading = androidx.lifecycle.MutableLiveData(false)

    /** 设置面板"刷新"按钮在加载中点击时调用——立即中止当前加载，丢弃排队。 */
    fun stopHiddenLoading() {
        binding.hiddenWebView.stopLoading()
        loadUrlQueue.clear()
        loadUrlQueueJob?.cancel()
        binding.hiddenWebViewProgress.visibility = View.GONE
        isHiddenLoading.postValue(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyReaderOrientation(globalConfig.readerOrientationMode.value ?: 0)
        super.onCreate(savedInstanceState)
        if (startUrl.isBlank()) {
            Log.e("URL is empty, finish BrowserReadModeActivity")
            finish()
            return
        }
        ActivityManger.finishSameType(this)
        binding = ActivityBrowserReadModeBinding.inflate(layoutInflater)
        val pageStyle = PageStyle.getStyle(globalConfig.readerPageStyle.value)
        enableEdgeToEdge(pageStyle.isDark)
        setContentView(binding.root)

        binding.hiddenWebView.init()
        binding.hiddenWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.hiddenWebViewProgress.progress = newProgress
                if (newProgress >= 100) {
                    binding.hiddenWebViewProgress.visibility = View.GONE
                    isHiddenLoading.postValue(false)
                } else {
                    binding.hiddenWebViewProgress.visibility = View.VISIBLE
                    isHiddenLoading.postValue(true)
                }
            }
        }
        // 阅读模式仅用于内容提取，禁止跳转外部 APP / 下载文件 / 触发自定义 scheme
        binding.hiddenWebView.webViewClient = ReadModeWebViewClient()

        setupInsets()
        setupPageLoader()
        setupTouchListener()
        setupSettingsObserver()
        setupTts()
        initBackPressed()

        applyPageStyle()
        applyTextSize()
        lifecycleScope.launch {
            // 加载关联的 WebBook
            loadWebBook()
            showChapter(startUrl)
        }
        binding.refresh.click {
            if (binding.ivRefresh.isSelected) {
                stopHiddenLoading()
            } else {
                refresh()
            }
        }
        isHiddenLoading.observe(this) { loading ->
            binding.ivRefresh.isSelected = loading == true
        }
        binding.toggleWebView.click {
            toggleHiddenWebViewVisible()
        }
    }

    /** 章节右侧抽屉是否处于打开状态 */
    private fun isChapterDrawerOpen(): Boolean {
        return binding.drawerLayout.isDrawerOpen(GravityCompat.END)
    }

    private fun notifyChapterDialogChanged() {
        (supportFragmentManager.findFragmentByTag(ChapterListDialogFragment.TAG) as? ChapterListDialogFragment)
            ?.refreshCurrentTab()
    }

    private fun recordReadModeHistory(url: String, data: ExtractResult?) {
        if (url.toHttpUrlOrNull() == null || data == null || data.content.isBlank() || data.content == "加载失败") {
            return
        }
        if (!isHistoryNavigation) {
            forwardHistoryStack.clear()
        }
        val isMark = readModeHistory.firstOrNull { it.url == url }?.isMark ?: false
        readModeHistory.removeAll { it.url == url }
        val title = data.title.ifBlank { webBook?.title ?: url }
        readModeHistory.add(HistoryItem(title, url, isMark = isMark))
        readModeHistory.sortDescending()
        while (readModeHistory.size > 100) {
            readModeHistory.removeAt(readModeHistory.lastIndex)
        }
        notifyChapterDialogChanged()
    }

    fun canGoBackReadModeHistory(): Boolean = readModeHistory.size > 1

    fun canGoForwardReadModeHistory(): Boolean = forwardHistoryStack.isNotEmpty()

    /** 按当前内存历史记录顺序后退：移除当前页，打开历史列表中的上一条记录，同时放入前进栈。 */
    fun goBackReadModeHistory(): Boolean {
        val curUrl = pageLoader.curChapter?.link ?: return false
        if (!canGoBackReadModeHistory()) return false
        val current = readModeHistory.firstOrNull { it.url == curUrl }
            ?: HistoryItem(pageLoader.curChapter?.title ?: curUrl, curUrl)
        readModeHistory.removeAll { it.url == curUrl }
        forwardHistoryStack.addLast(current)
        val target = readModeHistory.firstOrNull() ?: return false
        isHistoryNavigation = true
        showChapter(target.url)
        isHistoryNavigation = false
        notifyChapterDialogChanged()
        return true
    }

    fun goForwardReadModeHistory(): Boolean {
        if (!canGoForwardReadModeHistory()) return false
        val target = forwardHistoryStack.removeLast()
        isHistoryNavigation = true
        showChapter(target.url)
        isHistoryNavigation = false
        notifyChapterDialogChanged()
        return true
    }

    fun openDrawer() {
        var fragment = supportFragmentManager.findFragmentByTag(ChapterListDialogFragment.TAG) as? ChapterListDialogFragment
        if (fragment == null) {
            fragment = ChapterListDialogFragment()
            chapterFragment = fragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.chapter_list_drawer_container, fragment, ChapterListDialogFragment.TAG)
                .commitAllowingStateLoss()
        }
        binding.drawerLayout.openDrawer(GravityCompat.END)
        fragment.onShown()
    }

    fun closeChapterDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.END)
    }

    fun isHiddenWebViewVisible(): Boolean = binding.hiddenWebView.isVisible

    fun toggleHiddenWebViewVisible() {
        if (isHiddenWebViewVisible()) {
            lifecycleScope.launch {
                exportHiddenWebViewToReadMode()
                showReadModePageView()
            }
        } else {
            showHiddenWebView()
        }
    }

    /** 显示后台 WebView。显示前确保 WebView 正在展示当前阅读页 URL，不一致则自动加载。 */
    private fun showHiddenWebView() {
        val curUrl = pageLoader.curChapter?.link ?: startUrl
        if (curUrl.isNotBlank() && binding.hiddenWebView.url != curUrl) {
            binding.hiddenWebView.loadUrl(curUrl)
        }
        binding.readModePageView.visibility = View.GONE
        binding.hiddenWebView.visibility = View.VISIBLE
        binding.webViewContainer.show()
    }

    /**
     * 从正在显示的 WebView 直接导出内容到阅读模式，不重新加载页面。
     * 返回键只切回阅读视图不调用该方法；只有用户点 toggle_web_view 从 WebView 返回时才导出。
     */
    private suspend fun exportHiddenWebViewToReadMode() {
        val url = binding.hiddenWebView.url ?: pageLoader.curChapter?.link ?: return
        if (url.isBlank() || url == "about:blank") return
        val data = extractWithRetry(binding.hiddenWebView, 3) ?: return
        if (data.content.isBlank()) {
            data.content = "加载失败"
        }
        putExtractResult(url, data)
        showChapter(url,if (url == pageLoader.curChapter?.link) pageLoader.pagePos else null)
    }

    private fun showReadModePageView() {
        binding.hiddenWebView.visibility = View.GONE
        binding.readModePageView.visibility = View.VISIBLE
        binding.webViewContainer.gone()
    }

    // ==================== Setup ====================

    private fun enableEdgeToEdge(isDark: Boolean) {
        if (isDark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            )
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.displayCutout
            val safeLeft = maxOf(systemBars.left, cutout?.safeInsetLeft ?: 0)
            val safeTop = maxOf(systemBars.top, cutout?.safeInsetTop ?: 0)
            val safeRight = maxOf(systemBars.right, cutout?.safeInsetRight ?: 0)
            val safeBottom = maxOf(systemBars.bottom, cutout?.safeInsetBottom ?: 0)
            val params = DisplayParams()
            pageLoader.mDisplayParams.setInsets(
                left = params.insetLeft + safeLeft,
                top = params.insetTop + safeTop,
                right = params.insetRight + safeRight,
                bottom = params.insetBottom + safeBottom,
            )
            binding.webViewContainer.setPadding(0, safeTop, 0, safeBottom)
            insets
        }
    }

    private fun setupPageLoader() {
        pageLoader.also { loader ->
            binding.readModePageView.pageLoader = loader
            loader.initPageView(binding.readModePageView)
            loader.setOnPageChangeListener(object : PageLoader.OnPageChangeListener {
                override fun requestChapters(requestChapters: MutableList<TxtChapter>) {
                    val idx = requestChapters.indexOf(loader.curChapter ?: return)
                    val curChapter = requestChapters.getOrNull(idx) ?: return
                    val isPrev = loader.chapterPos == 0 && (loader.chapterCategory?.size ?: 0) > 1
                    lifecycleScope.launch(Dispatchers.IO) {
                        loadUrlForReadMode(curChapter.link, pagePos = if (isPrev) 10000 else null)
                    }
                }

                override fun onBookRecordChange(bean: BookRecordBean) {
                    val curChapter = loader.curChapter ?: return
                    val book = webBook ?: return
                    if (book.lastUrl == curChapter.link) {
                        book.offest = bean.pagePos
                        book.scrollOffset = bean.scrollOffset
                        updateWebBookProgress(book.lastUrl ?: "")
                    }
                }
            })
        }
        // 初始化净化替换规则
        lifecycleScope.launch {
            pageLoader.setReplacementRules(ReplacementRuleUseCase.getEnabledRules())
        }
    }

    private fun setupTouchListener() {
        binding.readModePageView.setTouchListener(object : PageView.TouchListener {
            override fun intercept(event: MotionEvent?): Boolean {
                if (isChapterDrawerOpen()) {
                    if (event?.action == MotionEvent.ACTION_UP) {
                        closeChapterDrawer()
                    }
                    return true
                }
                // 隐藏设置对话框
                return getSettingFragment()?.let {
                    if (it.isVisible && event?.action == MotionEvent.ACTION_UP) {
                        supportFragmentManager.beginTransaction()
                            .hide(it)
                            .commitAllowingStateLoss()
                    }
                    it.isVisible
                } ?: false
            }

            override fun onTap(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
                val actions = ReaderClickAreaAction.normalize(globalConfig.readerClickAreaActions.value)
                val cellIndex = ReaderClickAreaAction.cellIndexOf(x, y, viewWidth, viewHeight)
                val action = actions.getOrElse(cellIndex) { ReaderClickAreaAction.MENU }
                handleReaderClickAction(action)
            }
        })
    }

    private fun handleReaderClickAction(action: Int) {
        when (action) {
            ReaderClickAreaAction.PREV_PAGE -> binding.readModePageView.performPageTap(false)
            ReaderClickAreaAction.NEXT_PAGE -> binding.readModePageView.performPageTap(true)
            ReaderClickAreaAction.MENU -> {
                binding.readModeSettingContainer.visibility = View.VISIBLE
                getSettingFragment(create = true)?.let {
                    supportFragmentManager.beginTransaction()
                        .show(it)
                        .commitAllowingStateLoss()
                }
            }

            ReaderClickAreaAction.PREV_CHAPTER -> gotoPrevChapter()
            ReaderClickAreaAction.NEXT_CHAPTER -> gotoNextChapter()
            ReaderClickAreaAction.NONE -> {}
        }
    }

    private fun getSettingFragment(create: Boolean = false): BrowserReadModeSettingFragment? {
        val fm = supportFragmentManager
        val fragment = fm.findFragmentByTag(BrowserReadModeSettingFragment.TAG)
        if (fragment != null && fragment is BrowserReadModeSettingFragment) {
            return fragment
        }
        if (create) {
            val title = pageLoader.chapterCategory?.getOrNull(pageLoader.chapterPos)?.title ?: ""
            val newFragment = BrowserReadModeSettingFragment().apply {
                setArgs(title)
            }
            fm.beginTransaction()
                .add(R.id.read_mode_setting_container, newFragment, BrowserReadModeSettingFragment.TAG)
                .hide(newFragment)
                .commitAllowingStateLoss()
            return newFragment
        }
        return null
    }

    private fun setupSettingsObserver() {
        // observe reader config changes and apply to pageLoader
        val text = androidx.lifecycle.MediatorLiveData<Int>()
        text.addSource(globalConfig.readerFontSize) { text.value = it }
        text.addSource(globalConfig.readerLineSpacing) { text.value = it }
        text.addSource(globalConfig.readerParaSpacing) { text.value = it }
        text.addSource(globalConfig.readerLetterSpacing) { text.value = it }
        text.observe(this) {
            applyTextSize()
        }
        globalConfig.readerPageMode.observe(this) {
            pageLoader.setPageMode(PageMode.entries.getOrNull(it) ?: PageMode.SIMULATION)
        }
        globalConfig.readerPageStyle.observe(this) {
            pageLoader.setPageStyle(PageStyle.getStyle(it))
        }
        globalConfig.readerOrientationMode.observe(this) {
            applyReaderOrientation(it)
        }
        globalConfig.readerBrightnessPercent.observe(this) {
            applyReaderBrightness()
        }
        globalConfig.readerScreenKeepOn.observe(this) {
            if (it == true) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        globalConfig.readerJianFanMode.observe(this) {
            pageLoader.setJianFanMode(it)
        }
        globalConfig.readerTypesettingMode.observe(this) {
            pageLoader.setTypesettingMode(it)
        }
        globalConfig.readerFontFamily.observe(this) {
            if (it.isAsset) {
                if (it.resId == 0) {
                    pageLoader.setTypeface(null)
                } else {
                    val typeface = ResourcesCompat.getFont(this, it.resId)
                    pageLoader.setTypeface(typeface)
                }
            } else {
                val typeface = Typeface.createFromFile(File(it.path!!))
                pageLoader.setTypeface(typeface)
            }
        }
        // 净化替换规则变更
        observe<String>(EventKey.REPLACEMENT_RULES_CHANGED) {
            lifecycleScope.launch {
                pageLoader.setReplacementRules(ReplacementRuleUseCase.getEnabledRules())
            }
        }
    }

    private fun initBackPressed() {
        setOnBackPressed {
            val fragment = getSettingFragment()
            when {

                isChapterDrawerOpen() -> {
                    closeChapterDrawer()
                    true
                }

                fragment?.isVisible == true -> {
                    supportFragmentManager.beginTransaction()
                        .hide(fragment)
                        .commitAllowingStateLoss()
                    true
                }

                isHiddenWebViewVisible() -> {
                    showReadModePageView()
                    true
                }

//                goBackReadModeHistory() -> true 返回键不后退

                else -> false
            }
        }
    }

    // ==================== Content Extraction ====================

    private suspend fun loadWebBook() {
        if (webBookId.isBlank()) return
        try {
            webBook = webDao.getWebBookById(webBookId).firstOrNull()
        } catch (e: Exception) {
            Log.e("loadWebBook error", e)
        }
    }

    /** 如果当前 URL 与 WebBook 同域名，更新阅读进度（lastUrl/lastTitle/updateTime） */
    private fun updateWebBookProgress(url: String) {
        val book = webBook ?: return
        val data = extractCache.get(url) ?: return
        val urlHost = url.toHttpUrlOrNull()?.topPrivateDomain()
        val bookHost = book.url.toHttpUrlOrNull()?.topPrivateDomain()
        if (urlHost == null || bookHost == null || urlHost != bookHost) return
        updateWebBookJob?.cancel()
        updateWebBookJob = lifecycleScope.launch(Dispatchers.IO) {
            if (book.lastUrl != url) {
                book.lastUrl = url
                book.offest = 0
                book.scrollOffset = 0
                Log.i("updateWebBookProgress: url changed, reset offset and scrollOffset")
            }
            book.lastTitle = data.title.ifBlank { book.lastTitle }
            book.updateTime = System.currentTimeMillis()
            try {
                webDao.insertWebBook(book)
            } catch (e: Exception) {
                Log.e("updateWebBookProgress error", e)
            }
        }
    }

    fun loadUrlForReadMode(url: String, force: Boolean = false, pagePos: Int? = null) {
        loadUrlQueue.removeAll { it.url == url }
        loadUrlQueue.addLast(LoadUrlRequest(url, force, pagePos))
        if (loadUrlQueueJob?.isActive == true) return
        loadUrlQueueJob = lifecycleScope.launch {
            while (loadUrlQueue.isNotEmpty()) {
                val request = loadUrlQueue.removeFirst()
                loadUrlForReadModeInternal(request.url, request.force, request.pagePos)
            }
        }
    }

    private suspend fun loadUrlForReadModeInternal(url: String, force: Boolean = false, pagePos: Int? = null) {
        var data = extractCache.get(url) ?: if (!force) loadExtractFromDisk(url) else null
        Log.i("loadUrlForReadMode: loading url = $url, force = $force, cached = ${data != null}")
        if (data == null || force) {
            if (force) {
                // 强制刷新：WebView 可能已经停在同一个 url 且 progress==100，
                // 直接 loadUrl 会让等待循环立即通过、提取到旧内容；先清空状态再加载
                binding.hiddenWebView.loadUrl("about:blank")
                var blank = 0
                while (blank < 25) {
                    delay(100.milliseconds)
                    if (binding.hiddenWebView.progress >= 100) break
                    blank++
                }
            }
            binding.hiddenWebView.loadUrl(url)
            var retry = 0
            while (retry < 50) {
                delay(200.milliseconds)
                if (binding.hiddenWebView.url == url && binding.hiddenWebView.progress >= 100) break
                retry++
            }
            delay(300.milliseconds)
            data = extractWithRetry(binding.hiddenWebView) ?: ExtractResult("加载失败", "加载失败")
            if (data.content.isBlank()) {
                data.content = "加载失败"
            }
            putExtractResult(url, data)
        }
        // 当前页面正文刚刚到位，且抽屉处于打开状态，刷新当前 Tab
        if (pageLoader.curChapter?.link == url && isChapterDrawerOpen()) {
            notifyChapterDialogChanged()
        }
        if (pageLoader.curChapter?.link == url) {
            showChapter(url, pagePos)
        }
    }

    fun refresh() {
        val url = pageLoader.curChapter?.link
        if (url.isNullOrBlank()) {
            toast("当前没有加载的页面，无法刷新")
            return
        }
        // 同时清除提取缓存，确保走完整的重新加载路径
        extractCache.remove(url)
        loadUrlForReadMode(url, true, pagePos = pageLoader.pagePos)
    }

    fun openInBrowser(home: Boolean) {
        if (home) {
            readModeHistory.clear()
            forwardHistoryStack.clear()
            notifyChapterDialogChanged()
            showChapter(webBook?.url ?: startUrl)
        } else {
            val url = pageLoader.curChapter?.link ?: startUrl
            val id = webBookId.ifBlank { null }
            WebViewBrowserActivity.startActivity(this, url = url, id = id ?: "")
        }
    }

    private suspend fun extractWithRetry(wv: WebView, retryMax: Int = 10): ExtractResult? {
        var retry = 0
        var data: ExtractResult? = null
        val pageUrl = wv.url
        while (retry < retryMax) {
            data = wv.extractContent()
            val contentOk = data.content.isNotBlank()
            val hasNav = !data.nextUrl.isNullOrBlank()
            if (contentOk && hasNav) return data
            delay(300.milliseconds)
            retry++
        }
        Log.w(
            "extractWithRetry 失败 url=$pageUrl " +
                "contentLen=${data?.content?.length} " +
                "prev=${data?.prevUrl} prevText=${data?.prevText} " +
                "next=${data?.nextUrl} nextText=${data?.nextText} " +
                "allLinks=${data?.allLinks?.size}"
        )
        return data
    }

    fun gotoPrevChapter() {
        val url = pageLoader.curChapterData?.prevUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "没有上一章", Toast.LENGTH_SHORT).show()
            return
        }
        showChapter(url, pagePos = 10000)
    }

    fun gotoNextChapter() {
        val url = pageLoader.curChapterData?.nextUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "没有下一章", Toast.LENGTH_SHORT).show()
            return
        }
        showChapter(url, pagePos = 0)
    }

    fun showChapter(url: String, pagePos: Int? = null) {
        val data: ExtractResult? = extractCache.get(url)
        recordReadModeHistory(url, data)
        // 内存未命中 → 异步从磁盘读取，命中后再次渲染
        if (data == null && url.isNotBlank()) {
            lifecycleScope.launch {
                val disk = loadExtractFromDisk(url)
                if (disk != null && pageLoader.curChapter?.link == url) {
                    showChapter(url, pagePos)
                }
            }
        }
        // 保存到 LruCache，夜间模式切换导致 Activity 重建时直接恢复

        // 切换到不同的页面时，如抽屉打开则自动刷新；否则会在下次打开时根据当前 url 渲染
        val pageChanged = pageLoader.curChapter?.link != url

        // 更新 WebBook 阅读进度（同域名时）
        updateWebBookProgress(url)

        val book = Book().apply {
            this.url = webBook?.url ?: url.ifBlank { "browser://reader" }
            this.title = webBook?.title ?: "浏览器阅读模式"
        }

        val wb = webBook
        val record = BookRecordBean()
        if (wb?.lastUrl == url && needRestore && data != null) {
            needRestore = false
            record.apply {
                this.bookId = book.id
                this.pagePos = wb.offest
                this.scrollOffset = wb.scrollOffset
            }
        } else if (pagePos != null) {
            if (data != null) {
                needRestore = false
            }
            record.apply {
                this.bookId = book.id
                this.pagePos = pagePos
                this.scrollOffset = 0
            }
        } else {
            if (data != null) {
                needRestore = false
            }
        }

        ChapterPageCache.reset()
        TxtChapter.evictAll()

        val chapters = mutableListOf<TxtChapter>()
        data?.prevUrl?.let { url ->
            chapters.add(TxtChapter().apply {
                this.bookId = book.id
                this.link = url
                this.title = data.prevText ?: "上一页"
                this.chapterIndex = 0
                if (url.isBlank()) {
                    this.content = "上一页地址未找到，无法加载"
                }
            })
        }
        chapters.add(TxtChapter().apply {
            this.bookId = book.id
            this.link = url
            this.title = data?.title ?: "加载中"
            this.chapterIndex = chapters.size
            this.content = data?.content
        })
        data?.nextUrl?.let { url ->
            chapters.add(TxtChapter().apply {
                this.bookId = book.id
                this.link = url
                this.title = data.nextText ?: "下一页"
                this.chapterIndex = chapters.size
                if (url.isBlank()) {
                    this.content = "下一页地址未找到，无法加载"
                }
            })
        }

        pageLoader.closeBook()
        val chapter = chapters.indexOfFirst { it.link == url }
        record.chapter = chapter
        pageLoader.setBookRecord(record)
        pageLoader.mCollBook = book
        pageLoader.chapterCategory = chapters
        pageLoader.refreshChapterList()

        // 切到了新页面，若抽屉打开则立刻刷新当前 Tab；尚未提取时正文加载完成后会再次刷新
        if (pageChanged && isChapterDrawerOpen()) {
            notifyChapterDialogChanged()
        }
    }

    // ==================== Style / Immersive ====================

    private fun applyPageStyle() {
        val pageStyle = PageStyle.getStyle(globalConfig.readerPageStyle.value)
        enableEdgeToEdge(pageStyle.isDark)
        pageLoader.setPageStyle(pageStyle)
        val mode = PageMode.entries.getOrNull(globalConfig.readerPageMode.value ?: 0) ?: PageMode.SIMULATION
        pageLoader.setPageMode(mode)
    }

    private fun applyTextSize() {
        val ts = (globalConfig.readerFontSize.value ?: 0).toFloat()
        val ls = (globalConfig.readerLineSpacing.value ?: 0).toFloat()
        val ps = (globalConfig.readerParaSpacing.value ?: 0).toFloat()
        val lts = (globalConfig.readerLetterSpacing.value ?: 0).toFloat()
        if (ts > 0f) pageLoader.setTextSize(ts, ls, ps, lts / 100f)
    }

    private fun applyReaderOrientation(mode: Int) {
        requestedOrientation = when (mode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            3 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /** 应用阅读器亮度设置（-1 跟随系统，1..255 映射到 0..1）。与 BookReaderActivity 行为一致。 */
    private fun applyReaderBrightness() {
        val attrs = window.attributes ?: return
        val percent = globalConfig.readerBrightnessPercent.value ?: -1
        attrs.screenBrightness = if (percent < 0) {
            android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            percent.coerceIn(1, 255) / 255f
        }
        window.attributes = attrs
    }

    // ==================== Lifecycle ====================

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (globalConfig.readerVolumeKeyPageTurn.value == true) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    handleReaderClickAction(ReaderClickAreaAction.PREV_PAGE)
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    handleReaderClickAction(ReaderClickAreaAction.NEXT_PAGE)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        TtsService.stop()
        TtsService.progressChangeCallback = null
        TtsService.onCompleted = null
        extractJob?.cancel()
        loadUrlQueueJob?.cancel()
        loadUrlQueue.clear()
        binding.hiddenWebView.destroy()
    }

    // ==================== TTS ====================

    private fun setupTts() {
        TtsService.progressChangeCallback = { textLine ->
            lifecycleScope.launch {
                val loader = pageLoader
                if (loader.chapterPos != TtsService.chapterPos) {
                    loader.skipToChapter(TtsService.chapterPos)
                }
                val page = loader.curPageList?.find { page ->
                    page.lines.find { textLine == it } != null
                }
                loader.setTtsSpeakLine(textLine)
                if (page != null && page.position != loader.pagePos) {
                    loader.skipToPage(page.position)
                }
            }
        }
        TtsService.onCompleted = {
            pageLoader.setTtsSpeakLine(null)
            // 浏览器阅读模式只有一章，朗读完成后自动跳转下一章
            val url = pageLoader.curChapterData?.nextUrl
            if (!url.isNullOrBlank()) {
                Log.i("TTS 章节切换，继续朗读")
                showChapter(url)
                // 页面加载完成后自动开始朗读
                extractJob?.invokeOnCompletion {
                    if (TtsService.isSpeaking.value != true && pageLoader.curPageList?.isNotEmpty() == true) {
                        EventBus.post(EventKey.CHAPTER_SPEAK)
                    }
                }
            } else {
                Log.i("TTS 没有下一章，朗读结束")
            }
        }
        observe<String>(EventKey.CHAPTER_SPEAK) {
            lifecycleScope.launch {
                if (TtsService.isSpeaking.value == true) {
                    TtsService.stop()
                    pageLoader.setTtsSpeakLine(null)
                    Log.i("停止朗读")
                    return@launch
                }
                if (pageLoader.pageStatus == STATUS_LOADING) {
                    toast("正在加载中")
                    return@launch
                }
                if (pageLoader.curPageList.isNullOrEmpty()) {
                    toast("章节内容为空")
                    return@launch
                }
                Log.i("开始朗读 chapterPos:${pageLoader.chapterPos} pageSize:${pageLoader.curPageList?.size}")
                TtsService.start(pageLoader.chapterPos, pageLoader.curPageList, pageLoader.pagePos)
            }
        }
    }

    // ==================== Extract helpers ====================

    private data class LoadUrlRequest(
        val url: String,
        val force: Boolean,
        val pagePos: Int? = null,
    )

    data class ExtractResult(
        val title: String,
        var content: String,
        val prevUrl: String? = null,
        val nextUrl: String? = null,
        val prevText: String? = null,
        val nextText: String? = null,
        val allLinks: List<ChapterLink> = emptyList(),
    )

    companion object {
        @Volatile
        private var cachedJs: String? = null

        /**
         * 给外部（如浏览器切换阅读模式时的预提取）使用。
         * 复用同一份 browser_extract.js + JSON 解析逻辑。
         */
        suspend fun WebView.extractContent(): ExtractResult {
            val js = loadExtractJs(context)
            val pageUrl = this.url
            return suspendCancellableCoroutine { cont ->
                evaluateJavascript(js) { raw ->
                    try {
                        val jsonStr = unwrapJsString(raw)
                        val obj = JSONObject(jsonStr)
                        val title = obj.optString("title", "")
                        val content = obj.optString("content", "")
                        val prev = obj.optString("prev", "").takeIf { it.isNotBlank() }
                        val prevText = obj.optString("prevText", "").takeIf { it.isNotBlank() }
                        val next = obj.optString("next", "").takeIf { it.isNotBlank() }
                        val nextText = obj.optString("nextText", "").takeIf { it.isNotBlank() }
                        val linksArr = obj.optJSONArray("allLinks")
                        val allLinks = mutableListOf<ChapterLink>()
                        if (linksArr != null) {
                            for (i in 0 until linksArr.length()) {
                                val c = linksArr.getJSONObject(i)
                                allLinks.add(ChapterLink(c.optString("title"), c.optString("url")))
                            }
                        }
                        Log.i(
                            "extractContent url=$pageUrl title=$title contentLen=${content.length} " +
                                "prev=$prev prevText=$prevText next=$next nextText=$nextText " +
                                "allLinks=${allLinks.size}"
                        )
                        val debugArr = obj.optJSONArray("_debug")
                        if (debugArr != null) {
                            for (i in 0 until debugArr.length()) {
                                Log.i("extractContent debug ${i + 1}/${debugArr.length()}: ${debugArr.optString(i)}")
                            }
                        } else {
                            val debugObj = obj.optJSONObject("_debug")
                            if (debugObj != null) {
                                Log.i("extractContent _debug=$debugObj")
                            }
                        }
                        if (prev.isNullOrBlank() || next.isNullOrBlank()) {
                            Log.w(
                                "extractContent 缺少导航 url=$pageUrl prev=$prev next=$next " +
                                    "allLinks前20=${allLinks.take(20).joinToString { "${it.title}->${it.url}" }}"
                            )
                        }
                        if (cont.isActive) cont.resume(ExtractResult(title, content, prev, next, prevText, nextText, allLinks))
                    } catch (t: Throwable) {
                        Log.e("extractContent err raw=$raw", t)
                        if (cont.isActive) cont.resume(ExtractResult("", "", null, null))
                    }
                }
            }
        }

        private fun unwrapJsString(raw: String?): String {
            if (raw.isNullOrBlank() || raw == "null") return "{}"
            return if (raw.startsWith("\"") && raw.endsWith("\"")) {
                JSONObject("{\"v\":$raw}").optString("v", "{}")
            } else raw
        }

        /** 内存缓存：以 URL 为 key 缓存提取结果，夜间模式切换导致 Activity 重建时直接恢复 */
        val extractCache = LruCache<String, ExtractResult?>(10)

        /** 磁盘缓存：序列化 ExtractResult 到 cacheDir/browser_read_mode/，大小由用户配置 */
        @Volatile
        private var _diskCache: DiskCacheUtil? = null
        private val diskCache: DiskCacheUtil
            get() = _diskCache ?: synchronized(this) {
                _diskCache ?: createDiskCache().also { _diskCache = it }
            }

        private fun createDiskCache(): DiskCacheUtil {
            val sizeMB = globalConfig.browserReadModeCacheSizeMB.coerceAtLeast(1)
            return DiskCacheUtil(
                File(com.sjianjun.reader.App.app.cacheDir, "browser_read_mode"),
                maxSize = sizeMB.toLong() * 1024 * 1024,
            )
        }

        /** 用户修改缓存大小后调用，下一次访问会用新大小重建 DiskLruCache */
        fun resetDiskCache() {
            synchronized(this) { _diskCache = null }
        }

        /** 写入提取结果到内存 + 磁盘 */
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        fun putExtractResult(url: String, data: ExtractResult) {
            if (url.isBlank()) return
            extractCache.put(url, data)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    diskCache.put(url, data)
                } catch (e: Exception) {
                    Log.e("putExtractResult disk err", e)
                }
            }
        }

        /** 从磁盘读取提取结果；命中后回填内存，已有更新则跳过避免覆盖 */
        suspend fun loadExtractFromDisk(url: String): ExtractResult? {
            if (url.isBlank()) return null
            return try {
                val data = diskCache.getObj<ExtractResult>(url) ?: return null
                if (extractCache.get(url) == null) {
                    extractCache.put(url, data)
                }
                extractCache.get(url) ?: data
            } catch (e: Exception) {
                Log.e("loadExtractFromDisk err", e)
                null
            }
        }

        val PageLoader.curChapterData: ExtractResult?
            get() {
                val url = this.curChapter?.link ?: return null
                return extractCache.get(url)
            }

        /** 清除磁盘缓存（同时丢弃内存缓存） */
        suspend fun clearDiskCache() {
            extractCache.evictAll()
            diskCache.delete()
        }

        private fun loadExtractJs(context: Context): String {
            cachedJs?.let { return it }
            val text = context.applicationContext.assets
                .open("browser_extract.js")
                .bufferedReader()
                .use { it.readText() }
            cachedJs = text
            return text
        }

        fun startActivity(ctx: Context, url: String, webBookId: String? = null) {
            ctx.startActivity(
                android.content.Intent(ctx, BrowserReadModeActivity::class.java).apply {
                    putExtra(URL, url)
                    if (!webBookId.isNullOrBlank()) {
                        putExtra(WEB_ID, webBookId)
                    }
                }
            )
        }
    }

    data class ChapterLink(val title: String, val url: String)

    /**
     * 阅读模式专用 WebViewClient。
     * 主动导航走 Activity 的 navigateChecked；资源请求直接在 shouldInterceptRequest 内做同步判断。
     */
    private inner class ReadModeWebViewClient : android.webkit.WebViewClient() {

        /**
         * 当前主页面的顶级私有域。shouldInterceptRequest 在 WebView 工作线程上回调，
         * 不能直接调 view.url（必须主线程）；这里在 onPageStarted/doUpdateVisitedHistory
         * 自维护，资源拦截时直接读字段。
         */
        @Volatile
        private var currentHost: String? = null

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            currentHost = url?.toHttpUrlOrNull()?.topPrivateDomain()
            adBlock.markPage(url)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            // SPA pushState/replaceState 等不会触发 onPageStarted 的场景
            if (url != null) currentHost = url.toHttpUrlOrNull()?.topPrivateDomain()
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: android.webkit.WebResourceRequest,
        ): Boolean {
            val url = request.url.toString()
            navigateChecked(url, currentHost, onAllow = { view?.loadUrl(url) })
            return true
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            url ?: return false
            navigateChecked(url, currentHost, onAllow = { view?.loadUrl(url) })
            return true
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: android.webkit.WebResourceRequest,
        ): android.webkit.WebResourceResponse? {
            val url = request.url.toString()
            adBlock.addUrl(url)
            val target = url.toHttpUrlOrNull()?.topPrivateDomain()
            val isCross = currentHost != null && target != null && currentHost != target

            val block = when {
                target != null && adBlock.whitelist.contains(target) -> false
                adBlock.blacklist.contains(target) -> true
                isLikelyDownload(url) -> true
                isCross && crossOriginMode() == 2 -> true
                else -> false
            }
            return if (block) blockedResponse() else null
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: android.webkit.SslErrorHandler?,
            error: android.net.http.SslError?,
        ) {
            Log.e("ReadMode SSL: ${error?.toString()}")
            handler?.proceed()
        }

        private fun blockedResponse() = android.webkit.WebResourceResponse(
            "text/plain", "UTF-8",
            java.io.ByteArrayInputStream(ByteArray(0))
        )
    }

    // ==================== 广告拦截 / 跨域决策 ====================

    /** 跨域临时配置: -1=使用 globalConfig, 0=询问, 1=允许, 2=拒绝 */
    @Volatile
    private var crossOriginOverride: Int = -1

    private fun crossOriginMode(): Int =
        if (crossOriginOverride >= 0) crossOriginOverride else globalConfig.crossOriginMode

    /** 当前阅读章节所在的主域，用作跨域比较基准 */
    fun currentBaseHost(): String? =
        pageLoader.curChapter?.link?.toHttpUrlOrNull()?.topPrivateDomain()
            ?: webBook?.url?.toHttpUrlOrNull()?.topPrivateDomain()

    /** 简单按扩展名判断是否疑似下载链接 */
    private fun isLikelyDownload(url: String): Boolean {
        val lower = url.substringBefore('?').lowercase()
        val exts = arrayOf(
            ".apk", ".ipa", ".exe", ".msi", ".dmg",
            ".zip", ".rar", ".7z", ".tar", ".gz",
            ".mp3", ".mp4", ".mkv", ".avi", ".flv", ".m4a",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".epub", ".mobi", ".azw3"
        )
        return exts.any { lower.endsWith(it) }
    }

    /**
     * 主动导航的统一拦截入口：
     *  - 下载资源 / 黑名单 / 外部 scheme → onBlock
     *  - 白名单 / 同主域 / 跨域允许 → onAllow
     *  - 跨域+拒绝 → onBlock
     *  - 跨域+询问 → 弹窗，用户点"允许一次"或"+白名单"会触发 onAllow；选"+黑名单"或取消则均不回调
     */
    fun navigateChecked(
        url: String,
        baseHost: String?,
        onAllow: () -> Unit,
        onBlock: () -> Unit = {},
    ) {
        if (isLikelyDownload(url)) {
            onBlock(); return
        }

        val target = url.toHttpUrlOrNull()?.topPrivateDomain()
        if (target != null && adBlock.whitelist.contains(target)) {
            onAllow(); return
        }
        if (target != null && adBlock.blacklist.contains(target)) {
            onBlock(); return
        }

        val scheme = url.substringBefore(':', "").lowercase()
        if (scheme != "http" && scheme != "https") {
            if (scheme in setOf("about", "data", "blob", "javascript", "file")) onAllow() else onBlock()
            return
        }

        val isCross = baseHost != null && target != null && baseHost != target
        if (!isCross) {
            onAllow(); return
        }

        when (crossOriginMode()) {
            1 -> onAllow()
            2 -> onBlock()
            else -> askCrossOrigin(url, target, onAllow)
        }
    }

    /** 跨域询问弹窗（DialogCrossOriginBinding 三选一） */
    private fun askCrossOrigin(url: String, targetTop: String?, onAllow: () -> Unit) {
        try {
            val b = com.sjianjun.reader.databinding.DialogCrossOriginBinding
                .inflate(layoutInflater, null, false)
            b.tvDialogUrl.text = targetTop ?: url
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(b.root)
                .setCancelable(true)
                .create()
            b.btnAllowOnce.setOnClickListener {
                dialog.dismiss()
                onAllow()
            }
            b.btnWhitelist.setOnClickListener {
                if (targetTop != null) adBlock.addWhiteHost(HostStr(targetTop, listOf()))
                dialog.dismiss()
                onAllow()
            }
            b.btnBlacklist.setOnClickListener {
                if (targetTop != null) adBlock.addBlackHost(HostStr(targetTop, listOf()))
                dialog.dismiss()
            }
            dialog.show()
        } catch (t: Throwable) {
            Log.e("ReadMode askCrossOrigin err", t)
        }
    }

    class ChapterListAdapter(private val onClick: (ChapterLink) -> Unit) : androidx.recyclerview.widget.ListAdapter<ChapterLink, ChapterListAdapter.ViewHolder>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<ChapterLink>() {
            override fun areItemsTheSame(oldItem: ChapterLink, newItem: ChapterLink) = oldItem.url == newItem.url
            override fun areContentsTheSame(oldItem: ChapterLink, newItem: ChapterLink) = oldItem == newItem
        }
    ) {
        class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val textView: android.widget.TextView = view.findViewById(R.id.chapter_title)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_browser_chapter, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.textView.text = item.title
            holder.itemView.setOnClickListener { onClick(item) }
        }
    }
}
