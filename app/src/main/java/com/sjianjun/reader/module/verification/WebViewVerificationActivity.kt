package com.sjianjun.reader.module.verification

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.App
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ActivityVerificationBinding
import com.sjianjun.reader.http.WebViewPool
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.init
import com.sjianjun.reader.view.click
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import org.apache.commons.text.StringEscapeUtils
import sjj.alog.Log
import java.lang.ref.WeakReference
import java.util.UUID

class WebViewVerificationActivity : BaseActivity() {
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
    private var historyCleared = false
    private var pooled: WebViewPool.PooledWebView? = null
    private val webView: WebView? get() = pooled?.webView

    private val finishFallback by lazy {
        Runnable {
            val result = resultMap[intent.getStringExtra(KEY_RESULT)]
            result?.finished = true
            if (!isFinishing && !isDestroyed) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // 从共享池借用一个长驻 WebView，复用 cookie/会话，避免每次请求新建/销毁。
        GlobalScope.launch(Dispatchers.Main) {
            if (destroyed) return@launch
            val pw = WebViewPool.acquire()
            if (destroyed) {
                pw.release()
                return@launch
            }
            pooled = pw
            setupWebView(pw.webView, url, headerMap)
            startLoading(pw.webView)
        }
    }

    private fun startLoading(webView: WebView) {
        if (postData != null) {
            webView.postUrl(url, postData!!)
            Log.i("WebViewVerificationActivity loading URL with POST")
        } else {
            webView.loadUrl(url, headerMap)
            Log.i("WebViewVerificationActivity loading URL with GET")
        }
        webView.clearHistory()
        updateNavButtons(webView)
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyed = true
        runnable?.let { mHandler.removeCallbacks(it) }
        mHandler.removeCallbacks(finishFallback)
        val result = resultMap[intent.getStringExtra(KEY_RESULT)]
        result?.finished = true
        releasePooled()
    }

    override fun onPause() {
        super.onPause()
        pooled?.webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        pooled?.webView?.onResume()
    }

    private fun releasePooled() {
        val pw = pooled ?: return
        pooled = null
        val wv = pw.webView
        (wv.parent as? ViewGroup)?.removeView(wv)
        try {
            wv.stopLoading()
        } catch (_: Exception) {
        }
        runnable?.let { mHandler.removeCallbacks(it) }
        // 仅归还到池中复用，不销毁
        pw.release()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.verification_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_done -> {
                // 处理完成按钮点击事件
                finishWithCurrentContent()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initView() {
        binding.refresh.click {
            val wv = webView ?: return@click
            if (binding.refresh.isSelected) {
                wv.stopLoading()
            } else {
                wv.stopLoading()
                wv.loadUrl(wv.url ?: "")
            }
        }
        binding.backward.click {
            Log.i("后退")
            val wv = webView
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                Log.w("没有后退页面，退出")
            }
        }
        binding.forward.click {
            Log.i("前进")
            val wv = webView
            if (wv != null && wv.canGoForward()) {
                wv.goForward()
            } else {
                Log.w("没有前进页面")
            }
        }
        binding.settings.click {
            showSettingsDialog()
        }
        binding.complete.click {
            finishWithCurrentContent()
        }
        setSupportActionBar(binding.toolbar)
        setOnBackPressed {
            if (webView?.canGoBack() == true) {
                webView?.goBack()
                return@setOnBackPressed true
            }
            finishWithCurrentContent()
            true
        }
    }

    /**
     * 设置菜单：标题显示当前网页标题，URL 可编辑并跳转。
     */
    private fun showSettingsDialog() {
        val wv = webView ?: return
        val title = wv.title.orEmpty()
        val currentUrl = wv.url.orEmpty()
        val input = EditText(this).apply {
            setText(currentUrl)
            setSelection(currentUrl.length)
            inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (title.isNotBlank()) title else "网页设置")
            .setView(input)
            .setPositiveButton("跳转") { _, _ ->
                val target = input.text.toString().trim()
                if (target.isNotEmpty()) {
                    wv.loadUrl(target)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 根据当前 WebView 状态刷新底部导航按钮：
     * - 可后退时显示后退箭头；不可后退时切换为退出图标，点击退出当前页。
     * - 前进按钮按是否可前进启用/禁用。
     */
    private fun updateNavButtons(webView: WebView) {
        if (destroyed) return
        binding.backward.isEnabled = webView.canGoBack()
        binding.forward.isEnabled = webView.canGoForward()
    }

    private fun setupWebView(webView: WebView, url: String, headerMap: Map<String, String>) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        binding.webViewContainer.removeAllViews()
        binding.webViewContainer.addView(webView)
        webView.init(headerMap)
        historyCleared = false
        runnable = EvalJsRunnable(webView, url)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                Log.i("progress:${newProgress} ")
                binding.searchRefresh.progress = newProgress
                updateNavButtons(view)
                if (newProgress == 100) {
                    binding.refresh.isSelected = false
                    binding.searchRefresh.visibility = GONE
                } else {
                    binding.refresh.isSelected = true
                    binding.searchRefresh.visibility = VISIBLE
                }
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                Log.i("shouldOverrideUrlLoading:${request?.url}")
                if (url.endsWith(".apk")) {
                    return true
                }
                if (request?.url?.scheme == "http" || request?.url?.scheme == "https") {
                    // 处理 http 和 https 的链接
                    return false // 返回 false 以让 WebView 加载该链接
                }
                return true // 返回 true 以阻止 WebView 加载其他类型的链接
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }

            override fun onPageFinished(view: WebView?, url: String) {
                super.onPageFinished(view, url)
                // 借用的 WebView 可能带有上一次请求的页面历史，首次加载完成后清掉，避免用户后退到无关页面
                if (!historyCleared) {
                    historyCleared = true
                    view?.clearHistory()
                }
                view?.let { updateNavButtons(it) }
                runnable?.let { r ->
                    mHandler.removeCallbacks(r)
                    mHandler.postDelayed(r, 100)
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            }

        }
        updateNavButtons(webView)
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
        val wv = webView
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

    companion object {
        private val QUOTE_REGEX = "^\"|\"$".toRegex()
        private val KEY_URL = "url"
        private val KEY_HEADER_MAP = "header_map"
        private val KEY_POST_DATA = "post_data"
        private val KEY_VERIFICATION_KEY = "VerificationKey"
        private val KEY_RESULT = "result"
        private val resultMap = mutableMapOf<String, Result>()

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
         * - activity 迟迟未起（后台启动受限等），超过 [VERIFICATION_START_TIMEOUT_MS] 放行，避免永久阻塞后续请求。
         */
        private fun waitForResult(result: Result, url: String) {
            val startDeadline = System.currentTimeMillis() + VERIFICATION_START_TIMEOUT_MS
            while (!result.finished) {
                if (!result.started && System.currentTimeMillis() > startDeadline) {
                    Log.e("WebViewVerificationActivity not started within timeout for $url")
                    break
                }
                Thread.sleep(500)
            }
        }

    }

    private inner class EvalJsRunnable(
        webView: WebView,
        private val url: String,
        private val mJavaScript: String = "document.documentElement.outerHTML"
    ) : Runnable {
        var retry = 0
        private val mWebView: WeakReference<WebView> = WeakReference(webView)
        override fun run() {
            if (destroyed) return
            mWebView.get()?.evaluateJavascript(mJavaScript) {
                handleResult(it)
            }
        }

        private fun handleResult(result: String) = GlobalScope.launch {
            if (destroyed) return@launch
            if (!result.isBlank() && result != "null") {
                val content = parseJsString(result)
                Log.i("js执行结果获取成功：$url")
                val result = resultMap[intent.getStringExtra(KEY_RESULT)]
                result?.url = url
                result?.html = content
                val verificationKey: String by lazy { intent.getStringExtra(KEY_VERIFICATION_KEY) ?: "" }
                if (verificationKey.isNotBlank() && content.contains(verificationKey)) {
                    result?.finished = true
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
    }

}
