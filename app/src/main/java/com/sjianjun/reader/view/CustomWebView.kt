package com.sjianjun.reader.view

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sjianjun.reader.WEB_VIEW_UA_ANDROID
import com.sjianjun.reader.databinding.CustomWebViewBinding
import com.sjianjun.reader.module.bookcity.HostMgr
import com.sjianjun.reader.module.bookcity.contains
import com.sjianjun.reader.utils.setDarkening
import com.sjianjun.reader.utils.toast
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import java.io.ByteArrayInputStream


/*
 * Created by shen jian jun on 2020-07-10
 */
class CustomWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    val binding: CustomWebViewBinding =
        CustomWebViewBinding.inflate(LayoutInflater.from(context), this, true)
    private val lifecycleObserver by lazy { LifecycleObserver(this) }
    private var webView: WebView? = null
    private var url: String? = null
    private var clearHistory: Boolean = false
    private var hostMgr: HostMgr? = null
    fun init(owner: LifecycleOwner, hostMgr: HostMgr) {
        this.hostMgr = hostMgr
        webView = binding.webView
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.i("refresh")
            webView?.reload()
        }
        // 设置 WebView 的滚动监听器
        webView?.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // 如果 WebView 滚动到顶部，则允许 SwipeRefreshLayout 下拉刷新
            binding.swipeRefreshLayout.isEnabled = scrollY == 0
        }

        initWebView(binding.webView)
        owner.lifecycle.addObserver(lifecycleObserver)
    }

    fun loadUrl(url: String, clearHistory: Boolean = false) {
        this.url = url
        this.clearHistory = clearHistory
        Log.i("loadUrl:${url} ")
        webView?.loadUrl(url)
        binding.swipeRefreshLayout.isRefreshing = true
    }

    private fun initWebView(webView: WebView) {
        webView?.setOnLongClickListener {
            webView.evaluateJavascript(
                """
                javascript:(function() {
                    // 尝试获取 og:title 的内容
                    let ogTitle = document.querySelector('meta[property="og:title"]');

                    // 如果 og:title 存在，获取其 content 属性
                    if (ogTitle) {
                        return ogTitle.getAttribute('content')
                    } else {
                        // 如果 og:title 不存在，获取 keywords 的内容
                        let keywords = document.querySelector('meta[name="keywords"]');
                        if (keywords) {
                            return keywords.getAttribute('content')
                        } else {
                            return ""
                        }
                    }
                })()
            """.trimIndent()
            ) {
                Log.i("title:$it")
                val str = it?.toString()?.replace("\"", "")?.split(",")?.first()
                if (str.isNullOrBlank() || str == "null") {
                    return@evaluateJavascript
                }
                Log.i("复制到剪贴板:$str")
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipData = android.content.ClipData.newPlainText("text", str)
                clipboard?.setPrimaryClip(clipData)
                toast("已复制标题：${str}")
            }
            true
        }

        val cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true); // 启用 Cookie 支持
//        cookieManager.setAcceptThirdPartyCookies(webView, true); // 启用第三方 Cookie

        WebView.setWebContentsDebuggingEnabled(true)
//声明WebSettings子类
        val webSettings = webView.settings
        webSettings.setDarkening()
        webSettings.userAgentString = WEB_VIEW_UA_ANDROID
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
//设置自适应屏幕，两者合用
        webSettings.useWideViewPort = true //将图片调整到适合webview的大小
        webSettings.loadWithOverviewMode = true // 缩放至屏幕的大小

//缩放操作
        webSettings.setSupportZoom(true) //支持缩放，默认为true。是下面那个的前提。
        webSettings.builtInZoomControls = true //设置内置的缩放控件。若为false，则该WebView不可缩放
        webSettings.displayZoomControls = false //隐藏原生的缩放控件
        webView.scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        webView.isScrollbarFadingEnabled = false

        webView.webViewClient = object : WebViewClient() {


            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                Log.i("shouldOverrideUrlLoading:$url ")
                if (url.endsWith(".apk")) {
                    return true
                }
                val httpUrl = url.toHttpUrlOrNull()
                if (hostMgr?.blacklist.contains(httpUrl?.host) || hostMgr?.blacklist.contains(httpUrl?.topPrivateDomain())) {
                    return true
                }
//                val origin = this@CustomWebView.url?.toHttpUrlOrNull()
//                if (httpUrl?.topPrivateDomain() == origin?.topPrivateDomain()) {
//                    // 启动新 Activity
//                    EventBus.post(WEB_NEW_URL, url)
//                    return true
//                }
                return false

            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                Log.i(url)
                binding.swipeRefreshLayout.isRefreshing = true
            }

            override fun onPageFinished(webView: WebView?, url: String?) {
                Log.i(url ?: return)
                binding.swipeRefreshLayout.isRefreshing = false
                if (!url.startsWith("http")) {
                    return
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)

                if (clearHistory) {
                    clearHistory = false
                    webView?.clearHistory()
                }
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                Log.i("$url")
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                hostMgr?.addUrl(request.url.toString())
                if (hostMgr?.blacklist.contains(request.url.host) || hostMgr?.blacklist.contains(request.url.toString().toHttpUrlOrNull()?.topPrivateDomain())) {
                    Log.i("拦截请求：${request.url}")
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                }
                return super.shouldInterceptRequest(view, request)
            }

        }
        webView?.webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    Log.i("progress:${newProgress} ")
                }
            }
    }

    /**
     * 底部导航按钮设置
     */

    fun onBackPressed(): Boolean {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
            return true
        }
        return false
    }

    class LifecycleObserver(private val customWebView: CustomWebView) :
        DefaultLifecycleObserver {

        private val webView
            get() = customWebView.webView

        override fun onPause(owner: LifecycleOwner) {
            webView?.pauseTimers()
            webView?.onPause()
        }

        override fun onResume(owner: LifecycleOwner) {
            webView?.resumeTimers()
            webView?.onResume()
        }


        override fun onDestroy(owner: LifecycleOwner) {
            val parent = webView?.parent as? ViewGroup
            parent?.removeView(webView)
            webView?.destroy()
        }
    }


}
