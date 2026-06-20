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
import com.sjianjun.reader.utils.TtsService
import com.sjianjun.reader.utils.init
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import sjj.alog.Log
import sjj.novel.view.reader.bean.BookRecordBean
import sjj.novel.view.reader.page.DisplayParams
import sjj.novel.view.reader.page.NetPageLoader
import sjj.novel.view.reader.page.PageLoader
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

    private lateinit var binding: ActivityBrowserReadModeBinding
    private lateinit var hiddenWebView: WebView

    private var extractJob: Job? = null
    private var currentUrl: String? = null
    private var prevChapterUrl: String? = null
    private var nextChapterUrl: String? = null
    private var updateWebBookJob: Job? = null

    private val webDao get() = com.sjianjun.reader.repository.DbFactory.db.webBookDao()

    val pageLoader: NetPageLoader get() = _pageLoader
    private lateinit var _pageLoader: NetPageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        applyReaderOrientation(globalConfig.readerOrientationMode.value ?: 0)
        super.onCreate(savedInstanceState)
        if (startUrl.isBlank()) {
            Log.e("URL is empty, finish BrowserReadModeActivity")
            finish()
            return
        }
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
        setupButtons()
        setupSettingsObserver()
        setupTts()
        initBackPressed()

        applyPageStyle()
        applyTextSize()
        // 加载关联的 WebBook
        loadWebBook()
        val cached = extractCache.get(startUrl)
        if (cached != null) {
            // 从内存缓存恢复，不重新加载
            currentUrl = startUrl
            prevChapterUrl = cached.prevUrl
            nextChapterUrl = cached.nextUrl
            showChapter(cached.title, cached.content, startUrl)
        } else {
            loadUrlForReadMode(startUrl)
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
            _pageLoader.mDisplayParams.setInsets(
                left = params.insetLeft + safeLeft,
                top = params.insetTop + safeTop,
                right = params.insetRight + safeRight,
                bottom = params.insetBottom + safeBottom,
            )
            insets
        }
    }

    private fun setupPageLoader() {
        _pageLoader = NetPageLoader().also { loader ->
            binding.readModePageView.pageLoader = loader
            loader.initPageView(binding.readModePageView)
            loader.setOnPageChangeListener(object : PageLoader.OnPageChangeListener {
                override fun requestChapters(requestChapters: MutableList<TxtChapter>) {
                    // 用 LruCache 中对应 URL 的缓存填充章节内容
                    var filled = false
                    requestChapters.forEach { ch ->
                        if (ch.content.isNullOrBlank()) {
                            val link = ch.link ?: return@forEach
                            val cached = extractCache.get(link)
                            if (cached != null && cached.content.isNotBlank()) {
                                ch.content = cached.content
                                ch.title = cached.title
                                filled = true
                            }
                        }
                    }
                    if (filled && loader.pageStatus == STATUS_LOADING) {
                        loader.openChapter()
                    }
                }
                override fun onBookRecordChange(bean: BookRecordBean) {}
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
                setArgs(title) { finish() }
            }
            fm.beginTransaction()
                .add(R.id.read_mode_setting_container, newFragment, BrowserReadModeSettingFragment.TAG)
                .hide(newFragment)
                .commitAllowingStateLoss()
            return newFragment
        }
        return null
    }

    private fun setupButtons() {
        binding.btnReadModePrevChapter.setOnClickListener { gotoPrevChapter() }
        binding.btnReadModeNextChapter.setOnClickListener { gotoNextChapter() }
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
                // reloadPages() 会触发 requestChapters() 回调，
                pageLoader.reloadPages()
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

    private fun loadWebBook() {
        if (webBookId.isBlank()) return
        lifecycleScope.launch {
            try {
                webBook = webDao.getWebBookById(webBookId).firstOrNull()
            } catch (e: Exception) {
                Log.e("loadWebBook error", e)
            }
        }
    }

    /** 如果当前 URL 与 WebBook 同域名，更新阅读进度（lastUrl/lastTitle/updateTime） */
    private fun updateWebBookProgress(url: String, title: String) {
        val book = webBook ?: return
        val urlHost = url.toHttpUrlOrNull()?.topPrivateDomain()
        val bookHost = book.url.toHttpUrlOrNull()?.topPrivateDomain()
        if (urlHost == null || bookHost == null || urlHost != bookHost) return
        updateWebBookJob?.cancel()
        updateWebBookJob = lifecycleScope.launch {
            book.lastUrl = url
            book.lastTitle = title.ifBlank { book.lastTitle }
            book.updateTime = System.currentTimeMillis()
            try {
                webDao.insertWebBook(book)
            } catch (e: Exception) {
                Log.e("updateWebBookProgress error", e)
            }
        }
    }

    private fun loadUrlForReadMode(url: String) {
        currentUrl = url
        showLoadingChapter()
        hiddenWebView.loadUrl(url)
        extractJob?.cancel()
        extractJob = lifecycleScope.launch {
            var retry = 0
            while (retry < 50) {
                delay(200.milliseconds)
                if (hiddenWebView.url == url && hiddenWebView.progress >= 100) break
                retry++
            }
            delay(300.milliseconds)
            val data = extractWithRetry(hiddenWebView)
            if (data == null || data.content.isBlank()) {
                showErrorChapter()
                return@launch
            }
            prevChapterUrl = data.prevUrl
            nextChapterUrl = data.nextUrl
            showChapter(data.title, data.content, url)
        }
    }

    fun refresh() {
        val url = currentUrl
        if (url.isNullOrBlank()) {
            toast("当前没有加载的页面，无法刷新")
            return
        }
        loadUrlForReadMode(url)
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
        val url = prevChapterUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "没有上一章", Toast.LENGTH_SHORT).show()
            return
        }
        loadUrlForReadMode(url)
    }

    fun gotoNextChapter() {
        val url = nextChapterUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "没有下一章", Toast.LENGTH_SHORT).show()
            return
        }
        loadUrlForReadMode(url)
    }

    private fun showLoadingChapter() {
        showChapter("加载中", "正在加载...", currentUrl ?: "")
    }

    private fun showErrorChapter() {
        showChapter("错误", "无法提取页面内容", currentUrl ?: "")
    }

    private fun showChapter(title: String, content: String, url: String) {
        // 保存到 LruCache，夜间模式切换导致 Activity 重建时直接恢复
        extractCache.put(url, ExtractResult(title, content, prevChapterUrl, nextChapterUrl))

        // 更新 WebBook 阅读进度（同域名时）
        updateWebBookProgress(url, title)

        val book = Book().apply {
            this.url = url.ifBlank { "browser://reader" }
            this.title = title.ifBlank { "Reader" }
        }
        val txt = TxtChapter().apply {
            this.bookId = book.id
            this.link = url.ifBlank { "browser://reader" }
            this.title = title.ifBlank { "Reader" }
            this.chapterIndex = 0
            this.content = content
        }
        pageLoader.mCollBook = book
        pageLoader.chapterCategory = listOf(txt)
        pageLoader.refreshChapterList()
        pageLoader.openChapter()
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
        hiddenWebView.destroy()
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
            val url = nextChapterUrl
            if (!url.isNullOrBlank()) {
                Log.i("TTS 章节切换，继续朗读")
                loadUrlForReadMode(url)
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

    data class ExtractResult(
        val title: String,
        val content: String,
        val prevUrl: String?,
        val nextUrl: String?,
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
                    val next = obj.optString("next", "").takeIf { it.isNotBlank() }
                    if (cont.isActive) cont.resume(ExtractResult(title, content, prev, next))
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
        @Volatile private var cachedJs: String? = null

        /** 内存缓存：以 URL 为 key 缓存提取结果，夜间模式切换导致 Activity 重建时直接恢复 */
        val extractCache = LruCache<String, ExtractResult>(10)

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
}
