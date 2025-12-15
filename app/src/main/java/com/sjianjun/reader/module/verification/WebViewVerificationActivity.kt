package com.sjianjun.reader.module.verification

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
    private val html: String by lazy { intent.getStringExtra(KEY_HTML) ?: "" }
    private val encoding: String by lazy { intent.getStringExtra(KEY_ENCODING) ?: "UTF-8" }
    private val mHandler = Handler(Looper.getMainLooper())
    private val runnable by lazy { EvalJsRunnable(binding.webView, url) }

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
            binding.webView.loadDataWithBaseURL(url, html, "text/html", encoding, url)
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
        val result = resultMap[intent.getStringExtra(KEY_RESULT)]
        result?.finished = true
        mHandler.removeCallbacks(runnable)
        binding.webView.destroy()
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
        binding.refresh.click {

            if (binding.refresh.isSelected) {
                binding.webView.stopLoading()
            } else {
                binding.webView.stopLoading()
                binding.webView.loadUrl(binding.webView.url ?: "")
            }
        }
        binding.backward.click {
            Log.i("后退")
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                Log.w("没有后退页面")
                finish() // 关闭当前 Activity
            }
        }
        binding.forward.click {
            Log.i("前进")
            if (binding.webView.canGoForward()) {
                binding.webView.goForward()
            } else {
                Log.w("没有前进页面")
            }
        }
        binding.complete.click {
            finish() // 例如：关闭当前 Activity
        }
        setSupportActionBar(binding.toolbar)
    }

    private fun initWebView(url: String, headerMap: Map<String, String>) {
        CookieMgr.clearCookiesForUrl(url)
        binding.webView.init(headerMap)
        binding.webView.clearCache(true)
        binding.webView.webChromeClient = object : WebChromeClient() {
        }

        binding.webView.webViewClient = object : WebViewClient() {
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
                mHandler.removeCallbacks(runnable)
                mHandler.postDelayed(runnable, 100)
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
        private val KEY_ENCODING = "encoding"
        private val KEY_VERIFICATION_KEY = "VerificationKey"
        private val KEY_RESULT = "result"
        private val resultMap = mutableMapOf<String, Result>()

        @JvmStatic
        fun startAndWaitResult(url: String, headerMap: Map<String, String> = mapOf(), html: String = "", encoding: String = "UTF-8", verificationKey: String = ""): String? {
            Log.i("WebViewVerificationActivity startAndWaitResult URL: $url, headerMap: $headerMap, html: $html")
            if (Looper.myLooper() == Looper.getMainLooper()) {
                throw IllegalStateException("startAndWaitResult 必须在子线程中调用")
            }
            val keyResult = UUID.randomUUID().toString()
            val intent = Intent(App.app, WebViewVerificationActivity::class.java).apply {
                putExtra(KEY_URL, url)
                putExtra(KEY_HEADER_MAP, HashMap(headerMap))
                putExtra(KEY_HTML, html)
                putExtra(KEY_RESULT, keyResult)
                putExtra(KEY_ENCODING, encoding)
                putExtra(KEY_VERIFICATION_KEY, verificationKey)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val result = Result(url)
            resultMap[keyResult] = result
            GlobalScope.launch(Dispatchers.Main) {
                Log.i("WebViewVerificationActivity start for $url")
                App.app.startActivity(intent)
            }
            while (true) {
                if (result.finished) {
                    break
                }
                Thread.sleep(500)
            }
            resultMap.remove(keyResult)
            Log.i("WebViewVerificationActivity result for $url is done")
            return gson.toJson(Response(result.url, result.html))
        }

    }

    private inner class EvalJsRunnable(
        webView: WebView,
        private val url: String,
        private val mJavaScript: String = "document.documentElement.outerHTML"
    ) : Runnable {
        private val quoteRegex = "^\"|\"$".toRegex()
        var retry = 0
        private val mWebView: WeakReference<WebView> = WeakReference(webView)
        override fun run() {
            mWebView.get()?.evaluateJavascript(mJavaScript) {
                handleResult(it)
            }
        }

        private fun handleResult(result: String) = GlobalScope.launch {
            if (!result.isBlank() && result != "null") {
                val content = StringEscapeUtils.unescapeJson(result).replace(quoteRegex, "")
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
            if (isDestroyed) {
                return@launch
            }
            retry++
            mHandler.postDelayed(this@EvalJsRunnable, 1000)
        }

    }

    class Response(val url: String, val html: String)
    class Result(var url: String) {
        var html: String = ""
        var finished: Boolean = false
    }

}