package com.sjianjun.reader.module.verification

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
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
        binding.refresh.setOnClickListener {

            if (binding.refresh.isSelected) {
                binding.webView.stopLoading()
            } else {
                binding.webView.stopLoading()
                binding.webView.reload()
            }
        }
        binding.backward.setOnClickListener {
            Log.i("后退")
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                Log.w("没有后退页面")
            }
        }
        binding.forward.setOnClickListener {
            Log.i("前进")
            if (binding.webView.canGoForward()) {
                binding.webView.goForward()
            } else {
                Log.w("没有前进页面")
            }
        }
        binding.complete.setOnClickListener {
            finish() // 例如：关闭当前 Activity
        }
        setSupportActionBar(binding.toolbar)
    }

    private fun initWebView(url: String, headerMap: Map<String, String>) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true) // 启用 Cookie 支持
        CookieMgr.clearCookiesForUrl(url)

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
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            }

        }
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                Log.i("progress:${newProgress} ")
                binding.searchRefresh.progress = newProgress
                binding.backward.isEnabled = view.canGoBack()
                binding.forward.isEnabled = view.canGoForward()
                if (newProgress == 100) {
                    binding.refresh.isSelected = false
                    binding.searchRefresh.visibility = GONE
                } else {
                    binding.refresh.isSelected = true
                    binding.searchRefresh.visibility = VISIBLE
                }
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