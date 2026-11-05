package com.sjianjun.reader.module.verification

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sjianjun.reader.App
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ActivityVerificationBinding
import com.sjianjun.reader.module.bookcity.AdBlock
import com.sjianjun.reader.module.bookcity.CustomWebView
import com.sjianjun.reader.module.browser.BrowserTab
import com.sjianjun.reader.module.browser.TabHost
import com.sjianjun.reader.python.ScriptTaskCancelledException
import com.sjianjun.reader.python.ScriptTaskRegistry
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.view.click
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import org.apache.commons.text.StringEscapeUtils
import sjj.alog.Log
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * 人机验证页：直接承载浏览器的 [CustomWebView]（含其底部工具栏 / 地址栏 / 菜单），
 * 因此与书城独立浏览器外观完全统一；顶部额外提供一个“完成”按钮用于结束验证并把当前页面 HTML 回传脚本。
 *
 * 验证的“阻塞等待 + 回传 HTML + 脚本取消即关闭”语义全部保留在本次活动里（[resultMap] / [waitForResult] /
 * [ScriptTaskRegistry] 取消动作），未触碰 [com.sjianjun.reader.module.browser.TabRepository]，
 * 仅用一个本地 [BrowserTab] + [TabHost] 桩驱动 [CustomWebView]，不会污染用户的浏览器标签库。
 */
class WebViewVerificationActivity : BaseActivity(), TabHost {
    private lateinit var binding: ActivityVerificationBinding
    private val url: String by lazy { intent.getStringExtra(KEY_URL) ?: "" }
    private val headerMap: Map<String, String> by lazy {
        intent.getSerializableExtra(KEY_HEADER_MAP) as? Map<String, String> ?: mapOf()
    }
    private val postData: ByteArray? by lazy { intent.getByteArrayExtra(KEY_POST_DATA) }
    private val mHandler = Handler(Looper.getMainLooper())
    private var runnable: EvalJsRunnable? = null
    private var finishing = false

    @Volatile
    private var destroyed = false

    /** 当前承载验证的本地标签（不入库，活动销毁即丢弃） */
    private var currentTab: BrowserTab? = null

    /** true = 承载从后台 web_get 提升上来的、已加载好的 WebView（不再发起任何网络请求） */
    private val isHandoff: Boolean by lazy { intent.getBooleanExtra(KEY_HANDOFF, false) }

    /** handoff 模式下被提升到前台的那个 WebView 实例 */
    private var handoffWebView: WebView? = null

    /** 当前实际承载验证的 WebView */
    private val activeWebView: WebView?
        get() = handoffWebView ?: if (::binding.isInitialized) binding.customWebView.webView else null

    private val finishFallback by lazy {
        Runnable {
            val result = resultMap[intent.getStringExtra(KEY_RESULT)]
            result?.finished = true
            if (!isFinishing && !isDestroyed) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeActivity = WeakReference(this)
        // 标记已启动：供 waitForResult 判断 activity 是否真的起来（后台启动受限等场景下不会触发）
        resultMap[intent.getStringExtra(KEY_RESULT)]?.started = true
        enableEdgeToEdge()
        binding = ActivityVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initView()

        if (url.isEmpty()) {
            Log.e("WebViewVerificationActivity No URL provided")
            finish()
            return
        }

        setupVerificationWebView()
    }

    private fun setupVerificationWebView() {
        if (isHandoff) {
            setupHandoffWebView()
            return
        }
        val cwv = binding.customWebView
        // 人机验证期间禁止用户切换夜间模式（避免触发重建/脚本取消导致验证页退出）
        cwv.nightModeSwitchAllowed = false
        val tab = BrowserTab(url = url, transient = true)
        currentTab = tab
        cwv.attach(tab, this)
        // CustomWebView 自带工具栏 / 地址栏 / 菜单，与书城浏览器完全统一；生命周期作用域用活动自身
        cwv.init(this, AdBlock)
        cwv.loadInitial()
        // loadInitial 会按 tab.url 发起一次 GET；如调用方要求 POST / 自定义请求头，则用对应方式覆盖
        val wv = cwv.webView
        val post = postData
        when {
            post != null -> wv?.postUrl(url, post)
            headerMap.isNotEmpty() -> wv?.loadUrl(url, headerMap)
        }
        runnable = EvalJsRunnable(cwv.webView, url)
        mHandler.postDelayed(runnable!!, 500)
    }

    /**
     * 直接把后台那次请求已加载好的 WebView 显示到前台，**不加载任何 URL**，
     * 用户在这个实例上完成人机验证后点击"完成"，页面内容原样回传给那次 web_get 请求。
     */
    private fun setupHandoffWebView() {
        // 用 peek 而非取走：配置变更（旋转等）重建活动时仍能拿到同一个 WebView 继续显示，
        // 静态槽位由 showHandoffAndWaitHtml 在等待结束后统一清空。
        val wv = pendingHandoffWebView
        if (wv == null) {
            Log.e("WebViewVerificationActivity handoff WebView 不可用，直接结束验证")
            resultMap[intent.getStringExtra(KEY_RESULT)]?.finished = true
            finish()
            return
        }
        handoffWebView = wv
        binding.customWebView.visibility = View.GONE
        (wv.parent as? ViewGroup)?.removeView(wv)
        val lp = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT, 0
        ).apply {
            topToBottom = binding.appBarLayout.id
            bottomToBottom = binding.main.id
        }
        binding.main.addView(wv, lp)
        runCatching { wv.onResume() }
        runnable = EvalJsRunnable(wv, url)
        mHandler.postDelayed(runnable!!, 500)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeActivity?.get() === this) {
            activeActivity = null
        }
        destroyed = true
        runnable?.let { mHandler.removeCallbacks(it) }
        mHandler.removeCallbacks(finishFallback)
        // 配置变更重建时不要把这次验证判为"已结束"，否则等待中的脚本会被提前放行、
        // 同一个 WebView 也就无法挂到新活动上继续验证。
        val rebuilding = isChangingConfigurations
        val result = resultMap[intent.getStringExtra(KEY_RESULT)]
        if (!rebuilding) result?.finished = true
        if (isHandoff) {
            if (!rebuilding) {
                // 这个 WebView 属于后台请求，只从视图树摘下，销毁/回收由 BackstageWebView 统一处理
                handoffWebView?.let { (it.parent as? ViewGroup)?.removeView(it) }
            }
            handoffWebView = null
        } else {
            // 销毁 CustomWebView 内的 WebView，避免泄漏（cookie 由全局 CookieManager 保留，不影响会话）
            binding.customWebView.destroyWebView()
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching { activeWebView?.onPause() }
    }

    override fun onResume() {
        super.onResume()
        runCatching { activeWebView?.onResume() }
    }

    private fun initView() {
        binding.btnDone.click {
            finishWithCurrentContent()
        }
        setOnBackPressed {
            val wv = activeWebView
            if (wv?.canGoBack() == true) {
                wv.goBack()
                true
            } else {
                finishWithCurrentContent()
                true
            }
        }
    }

    /**
     * 关闭页面时抓取当前 WebView 内容作为返回结果。
     * evaluateJavascript 为异步，回调中写入结果后再 finish；
     * 并设置兜底定时器，避免回调未触发导致页面无法关闭。
     */
    private fun finishWithCurrentContent() {
        if (finishing) return
        finishing = true
        val resultKey = intent.getStringExtra(KEY_RESULT)
        val wv = activeWebView
        if (wv == null) {
            resultMap[resultKey]?.finished = true
            mHandler.removeCallbacks(finishFallback)
            if (!isFinishing && !isDestroyed) finish()
            return
        }
        val ok = runCatching {
            wv.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                val content = parseJsString(raw)
                resultMap[resultKey]?.let {
                    if (content.isNotEmpty()) it.html = content
                    it.finished = true
                }
                mHandler.removeCallbacks(finishFallback)
                if (!isFinishing && !isDestroyed) finish()
            }
        }.isSuccess
        if (!ok) {
            // WebView 可能已被脚本取消流程销毁，直接放行等待，避免卡住脚本线程
            resultMap[resultKey]?.finished = true
            mHandler.removeCallbacks(finishFallback)
            if (!isFinishing && !isDestroyed) finish()
            return
        }
        mHandler.postDelayed(finishFallback, 1500)
    }

    private fun parseJsString(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return StringEscapeUtils.unescapeJson(raw).replace(QUOTE_REGEX, "")
    }

    // -----------------------------------------------------------------------------------------
    // TabHost 桩：验证页只承载单个标签，跨标签动作（新建/管理/退出等）在此按“单标签验证”语义处理。
    // 其中退出类动作等价于“完成验证”；其余多标签动作无操作，避免把验证页变成完整浏览器。
    // -----------------------------------------------------------------------------------------
    override val tabId: String get() = currentTab?.id ?: ""
    override val tabCount: Int get() = 1

    override fun newTab(url: String, transient: Boolean, sourceTabId: String) {
        // 验证页不支持多标签，忽略
    }

    override fun newTabForPopup(sourceTabId: String, popupWebView: WebView) {
        // 验证页忽略弹窗新标签
    }

    override fun closeTab(id: String) {
        // 单标签验证页，忽略
    }

    override fun closeActiveTab(): Boolean {
        // 验证页只有单个标签，关闭（底部后退键退到栈底变成的叉叉）即退出验证并把当前页面内容回传脚本。
        // 语义上“关掉的是最后一个标签，浏览器已退出”，返回 false。
        finishWithCurrentContent()
        return false
    }

    override fun selectTab(id: String) {
    }

    override fun duplicateTab(id: String) {
    }

    override fun showTabsManager() {
    }

    override fun requestExit() {
        finishWithCurrentContent()
    }

    override fun activeExit() {
        finishWithCurrentContent()
    }

    override fun notifyTabChanged() {
    }

    override fun persistTabs() {
    }

    companion object {
        private val QUOTE_REGEX = "^\"|\"$".toRegex()
        private val KEY_URL = "url"
        private val KEY_HEADER_MAP = "header_map"
        private val KEY_POST_DATA = "post_data"
        private val KEY_VERIFICATION_KEY = "VerificationKey"
        private val KEY_RESULT = "result"
        private val KEY_HANDOFF = "handoff"
        private val resultMap = ConcurrentHashMap<String, Result>()

        /** 待提升到前台的后台 WebView。View 不能跨 Intent 传递，用静态槽位交接；取走即清空。 */
        @Volatile
        private var pendingHandoffWebView: WebView? = null

        /** 当前正在运行的验证页实例，脚本任务被取消时用它关掉页面 */
        @Volatile
        private var activeActivity: WeakReference<WebViewVerificationActivity>? = null

        /** 同一时刻只允许一个验证页运行；后续请求在锁上排队，等前一个 activity 完成后再启动。 */
        private val verificationLock = Any()

        /** activity 启动兜底超时：若届时 activity 仍未起来（如后台启动受限），放行后续请求，避免永久阻塞。 */
        private const val VERIFICATION_START_TIMEOUT_MS = 15 * 1000L

        /** 前台人工验证的最长等待：超过则关闭验证页并放行，避免 web_get 请求被永久挂起。 */
        private const val HANDOFF_MAX_WAIT_MS = 10 * 60 * 1000L

        fun postAndWaitResult(url: String, postData: String, verificationKey: String = ""): String? =
            synchronized(verificationLock) {
                Log.i("WebViewVerificationActivity postAndWaitResult URL: $url, postData: $postData")
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    throw IllegalStateException("startAndWaitResult 必须在子线程中调用")
                }
                // 调用前拦截：脚本任务已取消时不再弹出验证页，直接抛取消异常
                ScriptTaskRegistry.throwIfCurrentCancelled()
                val keyResult = UUID.randomUUID().toString()
                val intent = Intent(App.app, WebViewVerificationActivity::class.java).apply {
                    putExtra(KEY_URL, url)
                    putExtra(KEY_POST_DATA, postData.toByteArray())
                    putExtra(KEY_RESULT, keyResult)
                    putExtra(KEY_VERIFICATION_KEY, verificationKey)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val result = Result(url)
                resultMap[keyResult] = result
                GlobalScope.launch(Dispatchers.Main) {
                    // 等待开始前就被取消（例如脚本任务已取消）：不要再弹出验证页
                    if (result.finished) return@launch
                    Log.i("WebViewVerificationActivity start for $url")
                    App.app.startActivity(intent)
                }
                waitForResult(result, url)
                resultMap.remove(keyResult)
                Log.i("WebViewVerificationActivity result for $url is done")
                gson.toJson(Response(result.url, result.html))
            }

        /**
         * 把后台 web_get 已加载好的 WebView 提升到前台由人工处理，阻塞等待完成后返回最终页面 HTML。
         *
         * 与 [startAndWaitResult] 的区别：这里**不重新加载 URL**，前台展示的就是那次请求
         * 的同一个 WebView 实例，因此整个过程只发生一次网络请求，脚本的 web_get 也不会中断。
         * 必须在子线程调用（会阻塞等待用户操作）。
         */
        @JvmStatic
        fun showHandoffAndWaitHtml(
            webView: WebView,
            url: String,
            verificationKey: String = ""
        ): String? = synchronized(verificationLock) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                throw IllegalStateException("showHandoffAndWaitHtml 必须在子线程中调用")
            }
            ScriptTaskRegistry.throwIfCurrentCancelled()
            Log.i("WebViewVerificationActivity showHandoffAndWaitHtml URL: $url")
            val keyResult = UUID.randomUUID().toString()
            val intent = Intent(App.app, WebViewVerificationActivity::class.java).apply {
                putExtra(KEY_URL, url)
                putExtra(KEY_RESULT, keyResult)
                putExtra(KEY_VERIFICATION_KEY, verificationKey)
                putExtra(KEY_HANDOFF, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val result = Result(url)
            resultMap[keyResult] = result
            pendingHandoffWebView = webView
            GlobalScope.launch(Dispatchers.Main) {
                // 等待开始前就被取消（例如脚本任务已取消）：不要再弹出验证页
                if (result.finished) return@launch
                Log.i("WebViewVerificationActivity handoff start for $url")
                App.app.startActivity(intent)
            }
            try {
                waitForResult(result, url)
                Log.i("WebViewVerificationActivity handoff done for $url")
                result.html
            } catch (_: ScriptTaskCancelledException) {
                // 脚本任务被取消：不能把异常抛进 GlobalScope 协程（会变成未捕获异常导致崩溃），
                // 由 BackstageWebView 的取消流程统一收尾。
                Log.i("WebViewVerificationActivity handoff cancelled for $url")
                null
            } finally {
                pendingHandoffWebView = null
                resultMap.remove(keyResult)
            }
        }

        @JvmStatic
        fun startAndWaitResult(url: String, headerMap: Map<String, String> = mapOf(), verificationKey: String = ""): String? =
            synchronized(verificationLock) {
                Log.i("WebViewVerificationActivity startAndWaitResult URL: $url, headerMap: $headerMap")
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    throw IllegalStateException("startAndWaitResult 必须在子线程中调用")
                }
                // 调用前拦截：脚本任务已取消时不再弹出验证页，直接抛取消异常
                ScriptTaskRegistry.throwIfCurrentCancelled()
                val keyResult = UUID.randomUUID().toString()
                val intent = Intent(App.app, WebViewVerificationActivity::class.java).apply {
                    putExtra(KEY_URL, url)
                    putExtra(KEY_HEADER_MAP, HashMap(headerMap))
                    putExtra(KEY_RESULT, keyResult)
                    putExtra(KEY_VERIFICATION_KEY, verificationKey)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val result = Result(url)
                resultMap[keyResult] = result
                GlobalScope.launch(Dispatchers.Main) {
                    // 等待开始前就被取消（例如脚本任务已取消）：不要再弹出验证页
                    if (result.finished) return@launch
                    Log.i("WebViewVerificationActivity start for $url")
                    App.app.startActivity(intent)
                }
                waitForResult(result, url)
                resultMap.remove(keyResult)
                Log.i("WebViewVerificationActivity result for $url is done")
                gson.toJson(Response(result.url, result.html))
            }

        /**
         * 阻塞等待验证页完成。
         * - activity 起来后（[Result.started]）不限时等待用户完成；
         * - activity 迟迟未起（后台启动受限等），超过 [VERIFICATION_START_TIMEOUT_MS] 放行，避免永久阻塞后续请求；
         * - 脚本任务被取消（例如退出阅读页）时立刻结束等待、关掉验证页并抛取消异常，让脚本退出。
         */
        private fun waitForResult(result: Result, url: String) {
            val cancelAction: () -> Unit = {
                Log.i("WebViewVerificationActivity cancelled for $url")
                result.cancelled = true
                result.finished = true
                Handler(Looper.getMainLooper()).post {
                    activeActivity?.get()?.let { activity ->
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            activity.finish()
                        }
                    }
                }
            }
            val task = ScriptTaskRegistry.current()
            task?.addCancelAction(cancelAction)
            try {
                val startDeadline = System.currentTimeMillis() + VERIFICATION_START_TIMEOUT_MS
                val maxDeadline = System.currentTimeMillis() + HANDOFF_MAX_WAIT_MS
                while (!result.finished) {
                    if (!result.started && System.currentTimeMillis() > startDeadline) {
                        Log.e("WebViewVerificationActivity not started within timeout for $url")
                        break
                    }
                    if (result.started && System.currentTimeMillis() > maxDeadline) {
                        Log.e("WebViewVerificationActivity not finished within max wait for $url")
                        // 兜底：超过最长等待仍无结果，关闭验证页放行，避免 web_get 请求被永久挂起
                        activeActivity?.get()?.let { activity ->
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                activity.finish()
                            }
                        }
                        break
                    }
                    Thread.sleep(500)
                }
            } finally {
                task?.removeCancelAction(cancelAction)
            }
            if (result.cancelled) {
                throw ScriptTaskCancelledException()
            }
        }

    }

    private inner class EvalJsRunnable(
        private val webView: WebView?,
        private val url: String,
        private val mJavaScript: String = "document.documentElement.outerHTML"
    ) : Runnable {
        var retry = 0

        override fun run() {
            if (destroyed) return
            // 脚本取消时 WebView 可能已被后台流程销毁，此处不能让异常冒泡
            runCatching {
                webView?.evaluateJavascript(mJavaScript) { handleResult(it) }
            }
        }

        private fun handleResult(result: String) = GlobalScope.launch {
            if (destroyed) return@launch
            val res = resultMap[intent.getStringExtra(KEY_RESULT)]
            if (res?.finished == true) return@launch
            if (!result.isBlank() && result != "null") {
                val content = parseJsString(result)
                Log.i("js执行结果获取成功：$url")
                res?.url = url
                if (content.isNotEmpty()) res?.html = content
                val verificationKey = intent.getStringExtra(KEY_VERIFICATION_KEY) ?: ""
                if (verificationKey.isNotBlank() && content.contains(verificationKey)) {
                    res?.finished = true
                    mHandler.post {
                        if (!isFinishing && !isDestroyed) finish()
                    }
                    return@launch
                }
                // handoff 模式下用户操作会引发页面跳转，需持续轮询才能自动命中 verificationKey；
                // 非 handoff 模式保持原有"取到一次结果即停"的行为。
                if (!isHandoff) return@launch
            }
            if (isDestroyed || destroyed) return@launch
            retry++
            mHandler.postDelayed(this@EvalJsRunnable, 1000)
        }

    }

    class Response(val url: String, val html: String)
    class Result(var url: String) {
        var html: String = ""

        @Volatile
        var finished: Boolean = false

        @Volatile
        var started: Boolean = false

        /** 是否因为脚本任务被取消而结束 */
        @Volatile
        var cancelled: Boolean = false
    }

}
