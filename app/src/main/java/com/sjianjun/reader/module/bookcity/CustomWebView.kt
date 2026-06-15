package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.AttributeSet
import android.view.LayoutInflater
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.databinding.CustomWebViewBinding
import com.sjianjun.reader.module.verification.WebViewLifecycleObserver
import com.sjianjun.reader.preferences.DelegateSharedPref
import com.sjianjun.reader.preferences.dataPref
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.init
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.view.click
import com.tencent.mmkv.MMKV
import io.legado.app.utils.activity
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import java.io.ByteArrayInputStream

/*
 * Created by sjj on 2020-07-10
 */
class CustomWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var lifecycleScope: LifecycleCoroutineScope? = null
    private var webBook: WebBook? = null
    private var vm: BookCityViewModel? = null
    val binding: CustomWebViewBinding = CustomWebViewBinding.inflate(LayoutInflater.from(context), this, true)
    private val lifecycleObserver by lazy { WebViewLifecycleObserver(binding.webView) }
    private var webView: WebView? = null
    private var homeUrl: String? = null
    var adBlock: AdBlock? = null
    val history = MutableLiveData<List<HistoryItem>>()
    private var needClearHistory = false

    @Volatile
    private var currentUrl: String? = null

    // 记录最后一个正常加载的页面 URL，用于拦截后恢复（避免空页面中转导致 goBack 回到空白页）
    private var lastGoodUrl: String? = null

    /**
     * 跨域临时配置: -1=未设置(使用globalConfig), 0=询问, 1=允许, 2=拒绝
     * 当用户在设置页修改跨域配置后，此值被重置为-1
     */
    @Volatile
    var crossOriginOverride: Int = -1

    var exitWebView = false

    /** 获取当前生效的跨域模式 */
    private fun effectiveCrossOriginMode(): Int {
        return if (crossOriginOverride >= 0) crossOriginOverride else globalConfig.crossOriginMode
    }

    fun init(owner: LifecycleOwner, adBlock: AdBlock, webBook: WebBook?, vm: BookCityViewModel) {
        this.webBook = webBook
        this.adBlock = adBlock
        this.vm = vm
        webView = binding.webView

        initWebView(binding.webView)
        initView()
        owner.lifecycle.addObserver(lifecycleObserver)
        history.value = History.list.toMutableList()
        this.lifecycleScope = owner.lifecycleScope
    }

    fun loadUrl(url: String, clearHistory: Boolean = false) {
        this.homeUrl = url
        currentUrl = url
        Log.i("loadUrl:${url} ")
        webView?.stopLoading()
        webView?.loadUrl(url)
        needClearHistory = clearHistory
    }

    fun back(): Boolean {
        if (exitWebView || webView?.canGoBack() != true) {
            return false
        }
        val wv = webView ?: return false
        val list = wv.copyBackForwardList()
        val currentIndex = list.currentIndex
        // 向前（更早）查找第一个非广告页面（不在黑名单中或在白名单中）
        var targetIndex = -1
        for (i in currentIndex - 1 downTo 0) {
            val itemUrl = list.getItemAtIndex(i).url
            val host = itemUrl.toHttpUrlOrNull()?.topPrivateDomain()
            val isBlacklisted = adBlock?.blacklist?.contains(host) == true
            val isWhitelisted = adBlock?.whitelist?.contains(host) == true
            if (!isBlacklisted || isWhitelisted) {
                targetIndex = i
                break
            }
        }
        return if (targetIndex >= 0) {
            val steps = targetIndex - currentIndex
            wv.goBackOrForward(steps)
            true
        } else {
            // 没找到非广告的历史项
            false
        }
    }

    private fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            val offset = (imeInsets.bottom - systemBars.bottom).coerceAtLeast(0)
            binding.browserBookCityToolbar.translationY = -offset.toFloat()
            binding.webViewSettings.translationY = -offset.toFloat()

            insets
        }
        binding.webViewSettings.bind(binding.webView, effectiveCrossOriginMode()) {
            crossOriginOverride = -1
        }
        binding.refresh.click {

            if (binding.refresh.isSelected) {
                webView?.stopLoading()
            } else {
                webView?.stopLoading()
                webView?.loadUrl(webView?.url ?: "")
            }
        }
        binding.backward.click {
            exitWebView = true
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
        binding.forward.click {
            Log.i("前进")
            if (webView?.canGoForward() == true) {
                webView?.goForward()
            } else {
                Log.w("没有前进页面")
            }
        }
        binding.settings.setOnClickListener {
            Log.i("打开菜单")
            if (binding.webViewSettings.isVisible) {
                binding.webViewSettings.hide()
            } else {
                binding.webViewSettings.show()
            }
        }
        binding.home.click {
            Log.i("回到首页")
            webView?.stopLoading()
            binding.webView.loadUrl(webBook?.url ?: this.homeUrl ?: "https://www.baidu.com")
            needClearHistory = true
        }
    }


    private fun initWebView(webView: WebView) {
        webView.init()
        webView.webViewClient = object : WebViewClient() {


            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest
            ): Boolean {
                Log.i("shouldOverrideUrlLoading isForMainFrame:${request.isForMainFrame} hasGesture:${request.hasGesture()} isRedirect:${request.isRedirect} ${request.url}")
                val showBlockDialog = binding.webViewSettings.showBlockDialog(view, request, adBlock, effectiveCrossOriginMode())
                if (!showBlockDialog) {
                    currentUrl = request.url.toString()
                } else {
                    // 拦截后 WebView 内部历史栈已推入被拦截的 URL 但未加载，状态机卡死。
                    // 在返回栈中查找 lastGoodUrl，如果在当前位置就不处理，否则后退到它。
                    view?.post {
                        val fallbackUrl = lastGoodUrl ?: homeUrl
                        Log.i("lastGoodUrl:${lastGoodUrl}")
                        if (!fallbackUrl.isNullOrBlank()) {
                            val list = view.copyBackForwardList()
                            val currentIndex = list.currentIndex
                            val targetIndex = (currentIndex downTo 0).firstOrNull { i ->
                                list.getItemAtIndex(i).url == fallbackUrl
                            }
                            if (targetIndex != null && targetIndex < currentIndex) {
                                view.goBackOrForward(targetIndex - currentIndex)
                            } else {
                                Log.i("targetIndex:${targetIndex}")
                                if (targetIndex == null && view.canGoBack()) {
                                    view.goBack()
                                }
                            }
                        }
                    }
                }
                return showBlockDialog
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.i("onPageStarted:$url ")
                currentUrl = url
                adBlock?.markPage(url)
            }

            override fun onPageFinished(webView: WebView?, url: String?) {
                // 记录最后一个正常加载的页面（非空页面），用于拦截后恢复
                if (!url.isNullOrBlank() && url != "about:blank") {
                    // 延迟检查页面是否有实际内容，避免把空 HTML 中转页记为正常页面
                    webView?.postDelayed({
                        webView.evaluateJavascript(
                            "(function(){var t=document.body?document.body.innerText.trim():'';return t;})()"
                        ) { result ->
                            Log.i("html:${result}")
                            if (result.length > 10) {
                                lastGoodUrl = url
                            }
                        }
                    }, 300)
                }
                webBook?.also { book ->
                    val urlHost = url?.toHttpUrlOrNull()?.topPrivateDomain()
                    if (book.url.toHttpUrlOrNull()?.topPrivateDomain() == urlHost) {
                        book.lastUrl = url ?: ""
                        book.lastTitle = webView?.title ?: ""
                        book.updateTime = System.currentTimeMillis()
                        lifecycleScope?.launch {
                            vm?.insertWebBook(book)
                        }
                    }
                }

                if (needClearHistory) {
                    needClearHistory = false
                    webView?.post {
                        Log.i("清除历史记录完成")
                        webView.clearHistory()
                        binding.forward.isEnabled = webView.canGoForward()
                    }
                }
                Log.i("onPageFinished ${webView?.url} title:${webView?.title}")
                val list = history.value!!.toMutableList()
                webView?.url?.let { url ->
                    val item = HistoryItem(webView.title ?: "无标题", url)
                    item.isMark = list.firstOrNull { it.url == url }?.isMark ?: false
                    list.removeAll { item -> item.url == url }
                    list.add(item)
                    list.sortDescending()
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
//                Log.i("shouldInterceptRequest:${request.url} ")
                val requestHost = request.url.toString().toHttpUrlOrNull()?.topPrivateDomain()
                // 白名单域名不拦截
                if (requestHost != null && adBlock?.whitelist.contains(requestHost) == true) {
                    return super.shouldInterceptRequest(view, request)
                }
                // 黑名单域名直接拦截
                if (adBlock?.blacklist.contains(requestHost) == true) {
                    Log.i("拦截请求：${request.url}")
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream("".toByteArray())
                    )
                }

                // 跨域检查：非同域名子资源根据跨域配置处理
                val currentHost = currentUrl?.toHttpUrlOrNull()?.topPrivateDomain()
                val isCrossOrigin = requestHost != null && currentHost != null && requestHost != currentHost
                if (isCrossOrigin) {
                    when (effectiveCrossOriginMode()) {
                        2 -> {
                            // 拒绝：拦截非同域子资源，记录到hostList
                            Log.i("跨域拦截请求：${request.url}")
                            adBlock?.addUrl(request.url.toString())
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                ByteArrayInputStream("".toByteArray())
                            )
                        }
                        // 1=允许, 0=询问（子资源无法弹窗，默认放行）
                        else -> { /* 放行 */
                        }
                    }
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

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                Log.e("HTTP Error: ${errorResponse.statusCode} ${request.url}")
                // 仅记录日志，不拦截，让 WebView 自行处理错误页面
            }


        }
        webView?.webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    Log.i("progress:${newProgress} ")
                    binding.searchRefresh.progress = newProgress
                    binding.forward.isEnabled = webView.canGoForward()
                    if (newProgress == 100) {
                        binding.refresh.isSelected = false
                        binding.searchRefresh.visibility = GONE
                    } else {
                        binding.refresh.isSelected = true
                        binding.searchRefresh.visibility = VISIBLE
                    }
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    // window.open() 在当前 WebView 中加载，不创建新窗口
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    transport?.webView = view
                    resultMsg?.sendToTarget()
                    return true
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

    fun markHistory(item: HistoryItem) {
        item.isMark = !item.isMark
        val list = history.value!!.toMutableList()
        list.remove(item)
        list.add(0, item)
        list.sortDescending()
        History.list = list
        Log.i("markHistory:$list")
        history.postValue(list)
    }

    object History : DelegateSharedPref(MMKV.mmkvWithID("AppConfig_History")) {
        var list by dataPref("History", listOf<HistoryItem>())
    }

    class HistoryItem(var title: String, val url: String, val time: Long = System.currentTimeMillis(), var isMark: Boolean = false) : Comparable<HistoryItem> {
        override fun compareTo(other: HistoryItem): Int {
            if (isMark != other.isMark) return if (isMark) 1 else -1
            return time.compareTo(other.time)
        }

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

        override fun toString(): String {
            return "HistoryItem(title='$title', isMark=$isMark, time=$time, url='$url')"
        }

    }
}