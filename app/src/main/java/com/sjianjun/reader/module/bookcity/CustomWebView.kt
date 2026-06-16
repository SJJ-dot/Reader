package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
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
    private var lifecycleObserver: WebViewLifecycleObserver? = null
    private var homeUrl: String? = null
    var adBlock: AdBlock? = null
    val history = MutableLiveData<List<HistoryItem>>()

    @Volatile
    private var currentUrl: String? = null

    // 记录最后一个正常加载的页面 URL，用于拦截后恢复
    private var lastGoodUrl: String? = null

    /** WebView 栈：最后一个元素是当前可见的 WebView */
    private val webViewStack = mutableListOf<WebView>()

    /** 前进栈：后退时弹出的 WebView 暂存于此 */
    private val forwardStack = mutableListOf<WebView>()

    /** 当前可见的 WebView */
    private val currentWebView: WebView? get() = webViewStack.lastOrNull()

    /**
     * 跨域临时配置: -1=未设置(使用globalConfig), 0=询问, 1=允许, 2=拒绝
     */
    @Volatile
    var crossOriginOverride: Int = -1

    var exitWebView = false

    private fun effectiveCrossOriginMode(): Int {
        return if (crossOriginOverride >= 0) crossOriginOverride else globalConfig.crossOriginMode
    }

    fun init(owner: LifecycleOwner, adBlock: AdBlock, webBook: WebBook?, vm: BookCityViewModel) {
        this.webBook = webBook
        this.adBlock = adBlock
        this.vm = vm

        initView()
        history.value = History.list.toMutableList()
        this.lifecycleScope = owner.lifecycleScope
    }

    fun loadUrl(url: String) {
        this.homeUrl = url
        currentUrl = url
        Log.i("loadUrl:${url} ")

        // 销毁前进栈
        forwardStack.forEach { destroyWebView(it) }
        forwardStack.clear()

        // 创建新 WebView 压入栈
        val newWebView = createWebView(url)
        pushWebView(newWebView)
    }

    fun back(): Boolean {
        if (exitWebView) return false
        if (webViewStack.size <= 1) return false

        // 当前 WebView 移到前进栈
        val current = webViewStack.removeAt(webViewStack.lastIndex)
        forwardStack.add(current)
        hideWebView(current)

        // 显示上一个 WebView
        val previous = webViewStack.last()
        showWebView(previous)
        currentUrl = previous.url
        updateForwardButton()
        return true
    }

    fun canGoForward(): Boolean = forwardStack.isNotEmpty()

    fun goForward(): Boolean {
        if (forwardStack.isEmpty()) return false

        // 当前 WebView 隐藏
        hideWebView(webViewStack.last())

        // 从前进栈恢复
        val next = forwardStack.removeAt(forwardStack.lastIndex)
        webViewStack.add(next)
        showWebView(next)
        currentUrl = next.url
        updateForwardButton()
        return true
    }

    private fun createWebView(url: String): WebView {
        val webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            init()
            webViewClient = createWebViewClient()
            webChromeClient = createWebChromeClient()
        }
        webView.loadUrl(url)
        return webView
    }

    private fun pushWebView(webView: WebView) {
        // 隐藏当前 WebView
        currentWebView?.let { hideWebView(it) }

        webViewStack.add(webView)
        binding.webViewContainer.addView(webView)
        showWebView(webView)
        updateForwardButton()

        // 重新绑定 WebViewSettings 到当前 WebView
        binding.webViewSettings.bind(webView, effectiveCrossOriginMode()) {
            crossOriginOverride = -1
        }
    }

    private fun hideWebView(webView: WebView) {
        webView.visibility = GONE
        webView.onPause()
    }

    private fun showWebView(webView: WebView) {
        webView.visibility = VISIBLE
        webView.onResume()
    }

    private fun destroyWebView(webView: WebView) {
        webView.stopLoading()
        webView.settings.javaScriptEnabled = false
        webView.clearHistory()
        webView.removeAllViews()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun updateForwardButton() {
        binding.forward.isEnabled = forwardStack.isNotEmpty()
    }

    private fun createWebViewClient() = object : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest
        ): Boolean {
            Log.i("shouldOverrideUrlLoading isForMainFrame:${request.isForMainFrame} hasGesture:${request.hasGesture()} isRedirect:${request.isRedirect} ${request.url}")
            val showBlockDialog = binding.webViewSettings.showBlockDialog(view, request, adBlock, effectiveCrossOriginMode())
            if (!showBlockDialog) {
                // 放行：创建新 WebView 压入栈，后退时直接切换无需重新加载
                val url = request.url.toString()
                currentUrl = url
                view?.post {
                    forwardStack.forEach { destroyWebView(it) }
                    forwardStack.clear()
                    val newWebView = createWebView(url)
                    pushWebView(newWebView)
                }
                return true
            } else {
                // 拦截后 WebView 内部历史栈已推入被拦截的 URL 但未加载，状态机卡死。
                view?.post {
                    val fallbackUrl = lastGoodUrl ?: homeUrl
                    if (!fallbackUrl.isNullOrBlank()) {
                        val list = view.copyBackForwardList()
                        val currentIndex = list.currentIndex
                        val targetIndex = (currentIndex downTo 0).firstOrNull { i ->
                            list.getItemAtIndex(i).url == fallbackUrl
                        }
                        if (targetIndex != null && targetIndex < currentIndex) {
                            view.goBackOrForward(targetIndex - currentIndex)
                        } else if (targetIndex == null && view.canGoBack()) {
                            view.goBack()
                        }
                    }
                }
                return true
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            Log.i("onPageStarted:$url ")
            currentUrl = url
            adBlock?.markPage(url)
        }

        override fun onPageFinished(webView: WebView?, url: String?) {
            if (!url.isNullOrBlank() && url != "about:blank") {
                webView?.postDelayed({
                    webView.evaluateJavascript(
                        "(function(){var t=document.body?document.body.innerText.trim():'';return t;})()"
                    ) { result ->
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
            val requestHost = request.url.toString().toHttpUrlOrNull()?.topPrivateDomain()
            if (requestHost != null && adBlock?.whitelist.contains(requestHost) == true) {
                return super.shouldInterceptRequest(view, request)
            }
            if (adBlock?.blacklist.contains(requestHost) == true) {
                Log.i("拦截请求：${request.url}")
                return WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    ByteArrayInputStream("".toByteArray())
                )
            }

            val currentHost = currentUrl?.toHttpUrlOrNull()?.topPrivateDomain()
            val isCrossOrigin = requestHost != null && currentHost != null && requestHost != currentHost
            if (isCrossOrigin) {
                when (effectiveCrossOriginMode()) {
                    2 -> {
                        Log.i("跨域拦截请求：${request.url}")
                        adBlock?.addUrl(request.url.toString())
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream("".toByteArray())
                        )
                    }
                    else -> { /* 放行 */ }
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
            handler?.proceed()
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            Log.e("HTTP Error: ${errorResponse.statusCode} ${request.url}")
        }
    }

    private fun createWebChromeClient() = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (view != currentWebView) return
            binding.searchRefresh.progress = newProgress
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
            val transport = resultMsg?.obj as? WebView.WebViewTransport
            transport?.webView = view
            resultMsg?.sendToTarget()
            return true
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
        binding.webViewSettings.bind(currentWebView ?: WebView(context), effectiveCrossOriginMode()) {
            crossOriginOverride = -1
        }
        binding.refresh.click {
            if (binding.refresh.isSelected) {
                currentWebView?.stopLoading()
            } else {
                currentWebView?.stopLoading()
                currentWebView?.reload()
            }
        }
        binding.backward.click {
            exitWebView = true
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
        binding.forward.click {
            goForward()
        }
        binding.settings.setOnClickListener {
            if (binding.webViewSettings.isVisible) {
                binding.webViewSettings.hide()
            } else {
                binding.webViewSettings.show()
            }
        }
        binding.home.click {
            Log.i("回到首页")
            val home = webBook?.url ?: this.homeUrl ?: "https://www.baidu.com"
            // 销毁所有 WebView，重新开始
            webViewStack.forEach { destroyWebView(it) }
            webViewStack.clear()
            forwardStack.forEach { destroyWebView(it) }
            forwardStack.clear()
            loadUrl(home)
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
        history.postValue(list)
    }

    fun destroyAllWebViews() {
        webViewStack.forEach { destroyWebView(it) }
        webViewStack.clear()
        forwardStack.forEach { destroyWebView(it) }
        forwardStack.clear()
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
