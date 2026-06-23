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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.sjianjun.coroutine.launch
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
import com.sjianjun.reader.utils.init
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
import sjj.novel.view.reader.page.PageLoader.Companion.STATUS_FINISH
import sjj.novel.view.reader.page.PageLoader.Companion.STATUS_LOADING
import sjj.novel.view.reader.page.PageMode
import sjj.novel.view.reader.page.PageStyle
import sjj.novel.view.reader.page.PageView
import sjj.novel.view.reader.page.TxtChapter
import java.io.File
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class BrowserReadModeActivity : BaseActivity() {

    private val startUrl by lazy { intent.getStringExtra(URL) ?: "" }
    private val webBookId by lazy { intent.getStringExtra(WEB_ID) ?: "" }

    private var webBook: WebBook? = null
    private var needRestore = true

    private lateinit var binding: ActivityBrowserReadModeBinding
    private lateinit var hiddenWebView: WebView

    private var extractJob: Job? = null
    private val loadUrlQueue = ArrayDeque<LoadUrlRequest>()
    private var loadUrlQueueJob: Job? = null

    /** 是否包含跨域链接 */
    private var includeCrossDomain = false
    private var updateWebBookJob: Job? = null

    private val webDao get() = com.sjianjun.reader.repository.DbFactory.db.webBookDao()

    private fun updateNextToPrevMap(url: String, data: ExtractResult?) {
        val next = data?.nextUrl
        if (!next.isNullOrBlank() && url.isNotBlank()) {
            nextToPrevMap.put(next, url)
        }
    }

    val pageLoader get() = binding.readModePageView.pageLoader!!

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

        hiddenWebView = binding.hiddenWebView
        hiddenWebView.init()
        hiddenWebView.webChromeClient = WebChromeClient()

        setupInsets()
        setupPageLoader()
        setupTouchListener()
        setupChapterList()
        setupSettingsObserver()
        setupTts()
        initBackPressed()

        applyPageStyle()
        applyTextSize()
        lifecycleScope.launch {
            // 启动时一次性恢复 next→prev 反向映射，对长程向后翻页（回退到旧章节）友好
            NextToPrevStore.ensureLoaded()
            // 加载关联的 WebBook
            loadWebBook()
            showChapter(startUrl)
        }
    }

    private fun setupChapterList() {
        binding.chapterListRv.itemAnimator = null
        binding.chapterListRv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.chapterListRv.adapter = ChapterListAdapter { link ->
            showChapter(link.url)
        }
        // 跨域筛选
        binding.chapterListIncludeCrossDomain.setOnCheckedChangeListener { _, checked ->
            includeCrossDomain = checked
            renderChapterList()
        }
        binding.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                renderChapterList()
                // 若当前页尚未提取，触发一次正文加载；加载完成后会自动 renderChapterList()
                val curUrl = pageLoader.curChapter?.link
                if (!curUrl.isNullOrBlank() && extractCache.get(curUrl) == null) {
                    loadUrlForReadMode(curUrl)
                }
            }
        })
    }

    /** 抽屉是否处于打开状态 */
    private fun isChapterDrawerOpen(): Boolean {
        return binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.END)
    }

    /** 根据当前页缓存渲染抽屉列表，按 includeCrossDomain 过滤跨域 */
    private fun renderChapterList() {
        val curUrl = pageLoader.curChapter?.link
        val links = curUrl?.let { extractCache.get(it)?.allLinks } ?: emptyList()
        val baseHost = curUrl?.toHttpUrlOrNull()?.topPrivateDomain()
            ?: webBook?.url?.toHttpUrlOrNull()?.topPrivateDomain()
        val filtered = if (includeCrossDomain || baseHost == null) {
            links
        } else {
            links.filter { link ->
                val host = link.url.toHttpUrlOrNull()?.topPrivateDomain()
                host == null || host == baseHost
            }
        }
        (binding.chapterListRv.adapter as? ChapterListAdapter)?.submitList(filtered.toList())
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.END)
        renderChapterList()
        val curUrl = pageLoader.curChapter?.link
        if (!curUrl.isNullOrBlank() && extractCache.get(curUrl) == null) {
            loadUrlForReadMode(curUrl)
        }
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
            binding.chapterListDrawer.setPadding(0, safeTop, 0, safeBottom)
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
                fragment?.isVisible == true -> {
                    supportFragmentManager.beginTransaction()
                        .hide(fragment)
                        .commitAllowingStateLoss()
                    true
                }

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

    private fun loadUrlForReadMode(url: String, force: Boolean = false, pagePos: Int? = null) {
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
                hiddenWebView.loadUrl("about:blank")
                var blank = 0
                while (blank < 25) {
                    delay(100.milliseconds)
                    if (hiddenWebView.progress >= 100) break
                    blank++
                }
            }
            hiddenWebView.loadUrl(url)
            var retry = 0
            while (retry < 50) {
                delay(200.milliseconds)
                if (hiddenWebView.url == url && hiddenWebView.progress >= 100) break
                retry++
            }
            delay(300.milliseconds)
            data = extractWithRetry(hiddenWebView) ?: ExtractResult("加载失败", "加载失败")
            if (data.content.isBlank()) {
                data.content = "加载失败"
            }
            putExtractResult(url, data)
        }
        updateNextToPrevMap(url, data)
        // 当前页面正文刚刚到位，且抽屉处于打开状态，刷新抽屉链接列表
        if (pageLoader.curChapter?.link == url && isChapterDrawerOpen()) {
            renderChapterList()
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
            showChapter(webBook?.url ?: startUrl)
        } else {
            val url = pageLoader.curChapter?.link ?: startUrl
            val id = webBookId.ifBlank { null }
            WebViewBrowserActivity.startActivity(this, url = url, id = id ?: "")
        }
    }

    private suspend fun extractWithRetry(wv: WebView): ExtractResult? {
        var retry = 0
        var data: ExtractResult? = null
        while (retry < 5) {
            data = wv.extractContent()
            if (data.content.isNotBlank()) return data
            delay(200.milliseconds)
            retry++
        }
        return data
    }

    fun gotoPrevChapter() {
        val curUrl = pageLoader.curChapter?.link
        val url = (if (curUrl != null) nextToPrevMap.get(curUrl) else null) ?: pageLoader.curChapterData?.prevUrl
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

    private fun showChapter(url: String, pagePos: Int? = null) {
        val data: ExtractResult? = extractCache.get(url)
        // 内存未命中 → 异步从磁盘读取，命中后再次渲染
        if (data == null && url.isNotBlank()) {
            lifecycleScope.launch {
                val disk = loadExtractFromDisk(url)
                if (disk != null && pageLoader.curChapter?.link == url) {
                    showChapter(url, pagePos)
                }
            }
        }
        updateNextToPrevMap(url, data)
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
        val prevUrl = (if (url.isNotBlank()) nextToPrevMap.get(url) else null) ?: data?.prevUrl
        prevUrl?.let {
            chapters.add(TxtChapter().apply {
                this.bookId = book.id
                this.link = it
                this.title = (if (it == data?.prevUrl) data.prevText else extractCache.get(it)?.title) ?: "上一页"
                this.chapterIndex = 0
            })
        }
        chapters.add(TxtChapter().apply {
            this.bookId = book.id
            this.link = url
            this.title = data?.title ?: "加载中"
            this.chapterIndex = chapters.size
            this.content = data?.content
        })
        data?.nextUrl?.let {
            chapters.add(TxtChapter().apply {
                this.bookId = book.id
                this.link = it
                this.title = data.nextText ?: "加载中"
                this.chapterIndex = chapters.size
            })
        }

        pageLoader.closeBook()
        val chapter = chapters.indexOfFirst { it.link == url }
        record.chapter = chapter
        pageLoader.setBookRecord(record)
        pageLoader.mCollBook = book
        pageLoader.chapterCategory = chapters
        pageLoader.refreshChapterList()

        // 切到了新页面，若抽屉打开则立刻刷新链接；尚未提取时正文加载完成后会再次刷新
        if (pageChanged && isChapterDrawerOpen()) {
            renderChapterList()
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
        hiddenWebView.destroy()
        // 强制把待落盘的 next→prev 立刻写出
        NextToPrevStore.flushNow()
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
        val directory: String? = null,
        val allLinks: List<ChapterLink> = emptyList(),
    )

    private suspend fun WebView.extractContent(): ExtractResult {
        val js = loadExtractJs(context)
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
                    val directory = obj.optString("directory", "").takeIf { it.isNotBlank() }
                    val linksArr = obj.optJSONArray("allLinks")
                    val allLinks = mutableListOf<ChapterLink>()
                    if (linksArr != null) {
                        for (i in 0 until linksArr.length()) {
                            val c = linksArr.getJSONObject(i)
                            allLinks.add(ChapterLink(c.optString("title"), c.optString("url")))
                        }
                    }
                    if (cont.isActive) cont.resume(ExtractResult(title, content, prev, next, prevText, nextText, directory, allLinks))
                } catch (t: Throwable) {
                    Log.e("extractContent err", t)
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

    companion object {
        @Volatile
        private var cachedJs: String? = null

        /** 内存缓存：以 URL 为 key 缓存提取结果，夜间模式切换导致 Activity 重建时直接恢复 */
        val extractCache = LruCache<String, ExtractResult?>(10)

        /**
         * 阅读模式下一页链接往往比上一页准确，自行维护 next→prev 反向映射。
         * - 按插入顺序 FIFO 淘汰：超过 MAX 时移除最早添加的记录
         * - 写时防抖落盘，整张表以一个 JSON 条目存入 DiskCacheUtil
         * - 启动时一次性恢复，对长程向后翻页（重启后回退到旧章节）友好
         */
        val nextToPrevMap = NextToPrevStore


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

        /** 写入提取结果到内存 + 磁盘；同时回填 next→prev 反向映射 */
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        fun putExtractResult(url: String, data: ExtractResult) {
            if (url.isBlank()) return
            extractCache.put(url, data)
            val next = data.nextUrl
            if (!next.isNullOrBlank()) nextToPrevMap.put(next, url)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    diskCache.put(url, data)
                } catch (e: Exception) {
                    Log.e("putExtractResult disk err", e)
                }
            }
        }

        /** 从磁盘读取提取结果；命中后回填内存与反向映射，已有更新则跳过避免覆盖 */
        suspend fun loadExtractFromDisk(url: String): ExtractResult? {
            if (url.isBlank()) return null
            return try {
                val data = diskCache.getObj<ExtractResult>(url) ?: return null
                if (extractCache.get(url) == null) {
                    extractCache.put(url, data)
                    val next = data.nextUrl
                    if (!next.isNullOrBlank()) nextToPrevMap.put(next, url)
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
            nextToPrevMap.clear()
            diskCache.delete()
        }

        /**
         * next→prev 反向映射存储：
         * - 内存用 LinkedHashMap 保持插入顺序，超出 MAX 时淘汰最早添加的（FIFO）
         * - 用同一个 DiskCacheUtil（key = NEXT_TO_PREV_KEY）整张表存一个 JSON 条目
         * - 写入采用 2s 防抖，避免连续翻页频繁 IO
         */
        object NextToPrevStore {
            private const val MAX = 5000
            private const val KEY = "__next_to_prev_map__"
            private const val FLUSH_DELAY_MS = 2000L

            private val map = object : LinkedHashMap<String, String>(256, 0.75f, false) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                    return size > MAX
                }
            }

            @Volatile
            private var loaded = false
            @Volatile
            private var dirty = false
            @Volatile
            private var flushJob: Job? = null

            fun get(key: String): String? = synchronized(map) { map[key] }

            fun put(key: String, value: String) {
                if (key.isBlank() || value.isBlank()) return
                synchronized(map) {
                    // LinkedHashMap 的 put 不会调整既有 key 的顺序（accessOrder=false 也不重排插入顺序），
                    // 这里手动 remove 再 put，让最新写入的 key 排到末尾，
                    // 触发 removeEldestEntry 时优先淘汰真正最早添加且未再被更新的记录。
                    map.remove(key)
                    map[key] = value
                }
                dirty = true
                scheduleFlush()
            }

            fun clear() {
                synchronized(map) { map.clear() }
                dirty = true
                scheduleFlush()
            }

            /** 启动时调用：从磁盘恢复一次 */
            suspend fun ensureLoaded() {
                if (loaded) return
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    if (loaded) return@withContext
                    try {
                        val snapshot: LinkedHashMap<String, String>? = diskCache.getObj(KEY)
                        if (snapshot != null) {
                            synchronized(map) {
                                snapshot.forEach { (k, v) -> if (!map.containsKey(k)) map[k] = v }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("NextToPrevStore load err", e)
                    }
                    loaded = true
                }
            }

            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            private fun scheduleFlush() {
                if (flushJob?.isActive == true) return
                flushJob = GlobalScope.launch(Dispatchers.IO) {
                    delay(FLUSH_DELAY_MS)
                    flushNow()
                }
            }

            /** 立刻落盘待写入数据，供 onDestroy 调用 */
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            fun flushNow() {
                if (!dirty) return
                dirty = false
                try {
                    val snapshot = synchronized(map) { LinkedHashMap(map) }
                    // DiskCacheUtil.put(Any) 是 suspend，这里直接走 IO 协程
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            diskCache.put(KEY, snapshot)
                        } catch (e: Exception) {
                            Log.e("NextToPrevStore flush err", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NextToPrevStore flush err", e)
                }
            }
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
