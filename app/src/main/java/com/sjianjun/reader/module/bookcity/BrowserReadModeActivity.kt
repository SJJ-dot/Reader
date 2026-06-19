package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.URL
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.databinding.ActivityBrowserReadModeBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.TtsService
import com.sjianjun.reader.utils.init
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import sjj.alog.Log
import sjj.novel.view.reader.bean.BookRecordBean
import sjj.novel.view.reader.page.NetPageLoader
import sjj.novel.view.reader.page.PageLoader
import sjj.novel.view.reader.page.PageMode
import sjj.novel.view.reader.page.PageStyle
import sjj.novel.view.reader.page.PageView
import sjj.novel.view.reader.page.TxtChapter
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class BrowserReadModeActivity : BaseActivity() {

    private val startUrl by lazy { intent.getStringExtra(URL) ?: "" }

    private lateinit var binding: ActivityBrowserReadModeBinding
    private lateinit var hiddenWebView: WebView

    private var extractJob: Job? = null
    private var currentUrl: String? = null
    private var prevChapterUrl: String? = null
    private var nextChapterUrl: String? = null
    private var settingsVisible = false

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
        setContentView(binding.root)

        hiddenWebView = binding.hiddenWebView
        hiddenWebView.init()
        hiddenWebView.webChromeClient = WebChromeClient()

        setupInsets()
        setupPageLoader()
        setupTouchListener()
        setupButtons()
        setupSettingsObserver()

        enterImmersiveMode()
        applyPageStyle()
        applyTextSize()
        loadUrlForReadMode(startUrl)
    }

    // ==================== Setup ====================

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.readModeSettingContainer.setPadding(
                systemBars.left, systemBars.top, systemBars.right, systemBars.bottom
            )
            insets
        }
    }

    private fun setupPageLoader() {
        _pageLoader = NetPageLoader().also { loader ->
            binding.readModePageView.pageLoader = loader
            loader.initPageView(binding.readModePageView)
            loader.setOnPageChangeListener(object : PageLoader.OnPageChangeListener {
                override fun requestChapters(requestChapters: MutableList<TxtChapter>) {}
                override fun onBookRecordChange(bean: BookRecordBean) {}
            })
        }
    }

    private fun setupTouchListener() {
        binding.readModePageView.setTouchListener(object : PageView.TouchListener {
            override fun intercept(event: MotionEvent?): Boolean {
                if (settingsVisible && event?.action == MotionEvent.ACTION_UP) {
                    hideSettings()
                    return true
                }
                return false
            }

            override fun onTap(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
                val cx = viewWidth / 3f
                val cy = viewHeight / 3f
                if (x > cx && x < cx * 2 && y > cy && y < cy * 2) {
                    if (settingsVisible) hideSettings() else showSettings()
                }
            }
        })
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
    }

    // ==================== Settings Show/Hide ====================

    private fun showSettings() {
        settingsVisible = true
        binding.btnReadModePrevChapter.visibility = View.VISIBLE
        binding.btnReadModeNextChapter.visibility = View.VISIBLE
        binding.readModeSettingContainer.visibility = View.VISIBLE

        val title = pageLoader.chapterCategory?.getOrNull(pageLoader.chapterPos)?.title ?: ""
        val fm = supportFragmentManager
        var fragment = fm.findFragmentByTag(BrowserReadModeSettingFragment.TAG) as? BrowserReadModeSettingFragment
        if (fragment == null) {
            fragment = BrowserReadModeSettingFragment().apply {
                setArgs(title) { finish() }
            }
            fm.beginTransaction()
                .add(R.id.read_mode_setting_container, fragment, BrowserReadModeSettingFragment.TAG)
                .commitNowAllowingStateLoss()
        } else {
            fragment.setArgs(title) { finish() }
        }
        fm.beginTransaction().show(fragment).commitAllowingStateLoss()
    }

    private fun hideSettings() {
        settingsVisible = false
        val fm = supportFragmentManager
        val fragment = fm.findFragmentByTag(BrowserReadModeSettingFragment.TAG) as? BrowserReadModeSettingFragment
        if (fragment != null && fragment.isVisible) {
            fm.beginTransaction().hide(fragment).commitAllowingStateLoss()
        }
        binding.readModeSettingContainer.visibility = View.GONE
    }

    // ==================== Content Extraction ====================

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
        val url = currentUrl ?: return
        showLoadingChapter()
        extractJob?.cancel()
        extractJob = lifecycleScope.launch {
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

    private fun enterImmersiveMode() {
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
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

    override fun onDestroy() {
        super.onDestroy()
        extractJob?.cancel()
        hiddenWebView.destroy()
    }

    // ==================== TTS ====================

    // ==================== Extract helpers ====================

    private data class ExtractResult(
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

        private fun loadExtractJs(context: Context): String {
            cachedJs?.let { return it }
            val text = context.applicationContext.assets
                .open("browser_extract.js")
                .bufferedReader()
                .use { it.readText() }
            cachedJs = text
            return text
        }

        fun startActivity(ctx: Context, url: String) {
            ctx.startActivity(
                android.content.Intent(ctx, BrowserReadModeActivity::class.java).apply {
                    putExtra(URL, url)
                }
            )
        }
    }
}
