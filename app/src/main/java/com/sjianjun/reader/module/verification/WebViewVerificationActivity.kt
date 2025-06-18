package com.sjianjun.reader.module.verification

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sjianjun.reader.App
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ActivityVerificationBinding
import com.sjianjun.reader.http.CookieMgr
import com.sjianjun.reader.utils.setDarkening
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.util.UUID

class WebViewVerificationActivity : BaseActivity() {
    private lateinit var binding: ActivityVerificationBinding
    private val url: String by lazy { intent.getStringExtra(KEY_URL) ?: "" }
    private val headerMap: Map<String, String> by lazy {
        intent.getSerializableExtra(KEY_HEADER_MAP) as? Map<String, String> ?: mapOf()
    }
    private val html: String by lazy { intent.getStringExtra(KEY_HTML) ?: "" }
    private val resultKey: String by lazy { intent.getStringExtra(KEY_RESULT) ?: "" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initWebView(url, headerMap)

        initView()

        if (html.isNotEmpty()) {
            binding.webView.loadDataWithBaseURL(url, html, "text/html", "UTF-8", url)
            Log.i("WebViewVerificationActivity loading HTML content")
        } else if (url.isNotEmpty()) {
            binding.webView.loadUrl(url, headerMap)
        } else {
            Log.e("WebViewVerificationActivity No URL or HTML provided")
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        waitResultSet.remove(resultKey)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.verification_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_done -> {
                // 处理完成按钮点击事件
                finish() // 例如：关闭当前 Activity
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initView() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.i("refresh")
            binding.webView.reload()
        }
        // 设置 WebView 的滚动监听器
        binding.webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // 如果 WebView 滚动到顶部，则允许 SwipeRefreshLayout 下拉刷新
            binding.swipeRefreshLayout.isEnabled = scrollY == 0
        }
        setSupportActionBar(binding.toolbar)
    }

    private fun initWebView(url: String, headerMap: Map<String, String>) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true) // 启用 Cookie 支持
        CookieMgr.clearCookiesForUrl(url)
        CookieMgr.applyToWebView(url)
        val cookie2 = CookieManager.getInstance().getCookie(url)
        Log.i("cookie2: $cookie2")

        binding.webView.settings.apply {
            setDarkening()
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            headerMap["User-Agent"]?.let {
                userAgentString = it
            }
            cacheMode = WebSettings.LOAD_NO_CACHE // 禁用缓存
            domStorageEnabled = false // 禁用 DOM 存储
        }
        binding.webView.clearCache(true)
        binding.webView.webChromeClient = object : WebChromeClient() {
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request?.url?.scheme == "http" || request?.url?.scheme == "https") {
                    Log.i("shouldOverrideUrlLoading:${request.url}")
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
                val cookie = cookieManager.getCookie(url)
                if (cookie != null) {
                    CookieMgr.setCookie(url, cookie)
                }
                binding.swipeRefreshLayout.isRefreshing = false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                Log.i(url)
                binding.swipeRefreshLayout.isRefreshing = true
            }

        }

    }

    companion object {
        private val KEY_URL = "url"
        private val KEY_HEADER_MAP = "header_map"
        private val KEY_HTML = "html"
        private val KEY_RESULT = "result"
        private val waitResultSet = mutableSetOf<String>()

        @JvmStatic
        fun startAndWaitResult(url: String, headerMap: Map<String, String> = mapOf(), html: String = "") {
            Log.i("WebViewVerificationActivity startAndWaitResult URL: $url, headerMap: $headerMap, html: $html")
            if (Looper.myLooper() == Looper.getMainLooper()) {
                throw IllegalStateException("startAndWaitResult 必须在子线程中调用")
            }
            val key = UUID.randomUUID().toString()
            val intent = Intent(App.app, WebViewVerificationActivity::class.java).apply {
                putExtra(KEY_URL, url)
                putExtra(KEY_HEADER_MAP, HashMap(headerMap))
                putExtra(KEY_HTML, html)
                putExtra(KEY_RESULT, key)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            waitResultSet.add(key)
            GlobalScope.launch(Dispatchers.Main) {
                Log.i("WebViewVerificationActivity start for $url")
                App.app.startActivity(intent)
            }
            while (true) {
                if (!waitResultSet.contains(key)) {
                    break
                }
                Thread.sleep(500)
            }
            Log.i("WebViewVerificationActivity result for $url is done")
        }

    }

}