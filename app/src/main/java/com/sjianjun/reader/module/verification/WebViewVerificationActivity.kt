package com.sjianjun.reader.module.verification

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
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
        val cwv = binding.customWebView
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
        runnable = EvalJsRunnable(cwv, url)
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
        val result = resultMap[intent.getStringExtra(KEY_RESULT)]
        result?.finished = true
        // 销毁 CustomWebView 内的 WebView，避免泄漏（cookie 由全局 CookieManager 保留，不影响会话）
        binding.customWebView.destroyWebView()
    }

    override fun onPause() {
        super.onPause()
        binding.customWebView.webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.customWebView.webView?.onResume()
    }

    private fun initView() {
        binding.btnDone.click {
            finishWithCurrentContent()
        }
        setOnBackPressed {
            if (binding.customWebView.webView?.canGoBack() == true) {
                binding.customWebView.webView?.goBack()
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
        val wv = binding.customWebView.webView
        if (wv == null) {
            resultMap[resultKey]?.finished = true
            mHandler.removeCallbacks(finishFallback)
            if (!isFinishing && !isDestroyed) finish()
            return
        }
        wv.evaluateJavascript("document.documentElement.outerHTML") { raw ->
            val content = parseJsString(raw)
            resultMap[resultKey]?.let {
                if (content.isNotEmpty()) it.html = content
                it.finished = true
            }
            mHandler.removeCallbacks(finishFallback)
            if (!isFinishing && !isDestroyed) finish()
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
        private val resultMap = mutableMapOf<String, Result>()

        /** 当前正在运行的验证页实例，脚本任务被取消时用它关掉页面 */
        @Volatile
        private var activeActivity: WeakReference<WebViewVerificationActivity>? = null

        /** 同一时刻只允许一个验证页运行；后续请求在锁上排队，等前一个 activity 完成后再启动。 */
        private val verificationLock = Any()

        /** activity 启动兜底超时：若届时 activity 仍未起来（如后台启动受限），放行后续请求，避免永久阻塞。 */
        private const val VERIFICATION_START_TIMEOUT_MS = 15 * 1000L

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
                while (!result.finished) {
                    if (!result.started && System.currentTimeMillis() > startDeadline) {
                        Log.e("WebViewVerificationActivity not started within timeout for $url")
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
        private val customWebView: CustomWebView,
        private val url: String,
        private val mJavaScript: String = "document.documentElement.outerHTML"
    ) : Runnable {
        var retry = 0

        override fun run() {
            if (destroyed) return
            customWebView.webView?.evaluateJavascript(mJavaScript) {
                handleResult(it)
            }
        }

        private fun handleResult(result: String) = GlobalScope.launch {
            if (destroyed) return@launch
            if (!result.isBlank() && result != "null") {
                val content = parseJsString(result)
                Log.i("js执行结果获取成功：$url")
                val resultKey = intent.getStringExtra(KEY_RESULT)
                val res = resultMap[resultKey]
                res?.url = url
                res?.html = content
                val verificationKey: String by lazy { intent.getStringExtra(KEY_VERIFICATION_KEY) ?: "" }
                if (verificationKey.isNotBlank() && content.contains(verificationKey)) {
                    res?.finished = true
                    finish()
                }
                return@launch
            }
            if (isDestroyed || destroyed) {
                return@launch
            }
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
