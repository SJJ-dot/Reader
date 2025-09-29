package com.sjianjun.reader.view

import android.content.ClipboardManager
import android.content.Context
import android.net.http.SslError
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.databinding.CustomWebViewBinding
import com.sjianjun.reader.module.bookcity.AdBlock
import com.sjianjun.reader.module.bookcity.contains
import com.sjianjun.reader.preferences.DelegateSharedPref
import com.sjianjun.reader.preferences.dataPref
import com.sjianjun.reader.utils.init
import com.sjianjun.reader.utils.toast
import com.tencent.mmkv.MMKV
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
    private var homeUrl: String? = null
    var adBlock: AdBlock? = null
    var openMenu: () -> Unit = {}
    val history = MutableLiveData<List<HistoryItem>>()
    private var needClearHistory = false
    fun init(owner: LifecycleOwner, adBlock: AdBlock) {
        this.adBlock = adBlock
        webView = binding.webView

        initWebView(binding.webView)
        initView()
        owner.lifecycle.addObserver(lifecycleObserver)
        history.value = History.list.toMutableList()
    }

    fun loadUrl(url: String, clearHistory: Boolean = false) {
        this.homeUrl = url
        Log.i("loadUrl:${url} ")
        webView?.stopLoading()
        webView?.loadUrl(url)
        needClearHistory = clearHistory
    }

    fun back(): Boolean {
        Log.i("后退")
        if (webView?.canGoBack() == true) {
            webView?.goBack()
            return true
        } else {
            Log.w("没有后退页面")
            return false
        }
    }

    private fun initView() {
        binding.refresh.click {

            if (binding.refresh.isSelected) {
                webView?.stopLoading()
            } else {
                webView?.stopLoading()
                webView?.reload()
            }
        }
        binding.backward.click {
            back()
        }
        binding.forward.click {
            Log.i("前进")
            if (webView?.canGoForward() == true) {
                webView?.goForward()
            } else {
                Log.w("没有前进页面")
            }
        }
        binding.menu.click {
            Log.i("打开菜单")
            openMenu()
        }
        binding.home.click {
            Log.i("回到首页")
            webView?.stopLoading()
            webView?.loadUrl(this.homeUrl ?: "https://www.baidu.com")
            needClearHistory = true
        }
    }

    fun copyUrlToClipboard() {
        webView?.url?.let { url ->
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = android.content.ClipData.newPlainText("text", url)
            clipboard?.setPrimaryClip(clipData)
            toast("已复制到剪贴板：${url}")
        }
    }

    fun copyTitleToClipboard() {
        webView?.evaluateJavascript(
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
            val str = it?.replace("\"", "")?.split(",")?.first()
            if (str.isNullOrBlank() || str == "null") {
                toast("标题获取失败")
                return@evaluateJavascript
            }
            Log.i("复制到剪贴板:$str")
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = android.content.ClipData.newPlainText("text", str)
            clipboard?.setPrimaryClip(clipData)
            toast("已复制到剪贴板：${str}")
        }
    }

    private fun initWebView(webView: WebView) {
        val cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true); // 启用 Cookie 支持
        cookieManager.setAcceptThirdPartyCookies(webView, true); // 启用第三方 Cookie
////chrome://inspect   edge://inspect
//        if (BuildConfig.DEBUG){
//            WebView.setWebContentsDebuggingEnabled(true)
//        }
//声明WebSettings子类
        webView.settings.init()

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
                if (adBlock?.blacklist.contains(httpUrl?.topPrivateDomain())) {
                    return true
                }
                if (request.url?.scheme == "http" || request.url?.scheme == "https") {
                    // 处理 http 和 https 的链接
                    return false // 返回 false 以让 WebView 加载该链接
                }
                return true

            }

            override fun onPageFinished(webView: WebView?, url: String?) {
                if (needClearHistory) {
                    needClearHistory = false
                    webView?.post {
                        Log.i("清除历史记录完成")
                        webView.clearHistory()
                        binding.backward.isEnabled = webView.canGoBack()
                        binding.forward.isEnabled = webView.canGoForward()
                    }
                }
                Log.i("title:${webView?.title} ${webView?.url} ")
                val list = history.value!!.toMutableList()
                webView?.url?.let {url->
                    val item = HistoryItem(webView.title ?: "无标题", url)
                    list.removeAll { item ->  item.url == url }
                    list.add(0, item)
                    if (list.size > 100) {
                        list.removeAt(list.lastIndex)
                    }
                    History.list = list
                    history.postValue(list)
                }

            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {

                if (adBlock?.blacklist.contains(request.url.toString().toHttpUrlOrNull()?.topPrivateDomain())) {
                    Log.i("拦截请求：${request.url}")
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream("".toByteArray())
                    )
                }
                adBlock?.addUrl(request.url.toString())
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Log.e("SSL Error: ${error?.toString()}")
                // 忽略 SSL 错误
                handler?.proceed()
            }


        }
        webView?.webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    Log.i("progress:${newProgress} ")
                    binding.searchRefresh.progress = newProgress
                    binding.backward.isEnabled = webView.canGoBack()
                    binding.forward.isEnabled = webView.canGoForward()
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

    fun clearHistory() {
        History.list = mutableListOf()
        history.postValue(mutableListOf())
    }

    fun removeHistory(item: HistoryItem) {
        val list = history.value!!.toMutableList()
        list.remove(item)
        History.list = list
        history.postValue(list)
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

    object History : DelegateSharedPref(MMKV.mmkvWithID("AppConfig_History")) {
        var list by dataPref("History", listOf<HistoryItem>())
    }

    class HistoryItem(val title: String, val url: String, val time: Long = System.currentTimeMillis()) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as HistoryItem

            if (time != other.time) return false
            if (title != other.title) return false
            if (url != other.url) return false

            return true
        }

        override fun hashCode(): Int {
            var result = time.hashCode()
            result = 31 * result + title.hashCode()
            result = 31 * result + url.hashCode()
            return result
        }
    }
}
