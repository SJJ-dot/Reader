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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.databinding.CustomWebViewBinding
import com.sjianjun.reader.databinding.DialogCrossOriginBinding
import com.sjianjun.reader.preferences.DelegateSharedPref
import com.sjianjun.reader.preferences.dataPref
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.init
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.view.click
import com.tencent.mmkv.MMKV
import io.legado.app.utils.activity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import java.io.ByteArrayInputStream
import kotlin.collections.remove
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/*
 * Created by sjj on 2020-07-10
 */
class CustomWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    internal var lifecycleScope: LifecycleCoroutineScope? = null
    private var updateWebBookJob: Job? = null
    var webBook: WebBook? = null
    private var vm: BookCityViewModel? = null
    val binding: CustomWebViewBinding = CustomWebViewBinding.inflate(LayoutInflater.from(context), this, true)
    var adBlock: AdBlock? = null
    val history = MutableLiveData<List<HistoryItem>>()

    /** WebView 栈：最后一个元素是当前可见的 WebView */
    private val webViewStack = mutableListOf<MyWebView>()

    /** 前进栈：后退时弹出的 WebView 暂存于此 */
    private val forwardStack = mutableListOf<MyWebView>()

    /** 当前可见的 WebView */
    val currentWebView: MyWebView? get() = webViewStack.lastOrNull()

    /**
     * 跨域临时配置: -1=未设置(使用globalConfig), 0=询问, 1=允许, 2=拒绝
     */
    @Volatile
    var crossOriginOverride: Int = -1

    fun effectiveCrossOriginMode(): Int {
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

    fun loadUrl(url: String, request: WebResourceRequest? = null) {
        Log.i("loadUrl:${url} ")
        if (request?.isRedirect == true) {
            // 如果是重定向，销毁上一个 WebView，避免前进栈中出现空白页
            currentWebView?.let { destroyWebView(it) }
            webViewStack.remove(currentWebView)
        }
        // 销毁前进栈
        forwardStack.forEach { destroyWebView(it) }
        forwardStack.clear()

        // 创建新 WebView 压入栈
        val newWebView = createWebView(url)
        pushWebView(newWebView)

    }

    fun removeBlankWebView() {
        // 先清理前进栈中的空白页
        val forwardIterator = forwardStack.iterator()
        while (forwardIterator.hasNext()) {
            val webView = forwardIterator.next()
            if (webView.isBlank == true) {
                destroyWebView(webView)
                forwardIterator.remove()
            }
        }

        // 清理栈中的空白页，但保留最后一个非空白页可见
        val iterator = webViewStack.iterator()
        while (iterator.hasNext()) {
            val webView = iterator.next()
            if (webView.isBlank == true) {
                destroyWebView(webView)
                iterator.remove()
            }
        }

        // 确保至少有一个可见的 WebView
        if (webViewStack.isNotEmpty() && currentWebView?.visibility != VISIBLE) {
            showWebView(webViewStack.last())
        }
        updateForwardButton()
    }

    fun back(): Boolean {
        if (webViewStack.size <= 1) return false

        // 当前 WebView 弹出栈
        val current = webViewStack.removeAt(webViewStack.lastIndex)
        hideWebView(current)

        // 显示上一个 WebView
        val previous = webViewStack.last()
        showWebView(previous)

        // 如果当前页面为空，直接销毁，不放入前进栈
        if (current.isBlank == true) {
            destroyWebView(current)
        } else {
            forwardStack.add(current)
        }
        updateForwardButton()
        // 如果切换到的上一个页面也是空白页，继续后退跳过它
        if (previous.isBlank == true) {
            return back()
        }
        return true
    }

    fun goForward(): Boolean {
        if (forwardStack.isEmpty()) return false

        // 当前 WebView 隐藏
        hideWebView(webViewStack.last())

        // 从前进栈恢复
        val next = forwardStack.removeAt(forwardStack.lastIndex)
        webViewStack.add(next)
        showWebView(next)
        updateForwardButton()
        return true
    }

    private fun createWebView(url: String): MyWebView {
        val webView = MyWebView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            init()
            webViewClient = createWebViewClient()
            webChromeClient = createWebChromeClient()
        }
        webView.currentHost = url
        webView.loadUrl(url)
        return webView
    }

    private fun pushWebView(webView: MyWebView) {
        // 隐藏当前 WebView
        currentWebView?.let { hideWebView(it) }

        webViewStack.add(webView)
        binding.webViewContainer.addView(webView)
        showWebView(webView)
        updateForwardButton()

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
        Log.i("销毁 WebView: ${webView.currentHost} isBlank:${webView.isBlank}")
    }

    private fun updateForwardButton() {
        binding.forward.isEnabled = forwardStack.isNotEmpty()
        binding.refresh.isSelected = currentWebView?.isBlank == false && (currentWebView?.progress ?: 100) < 100
    }

    // 抽取更新 webBook 的逻辑：在同一主域下更新 lastUrl/lastTitle/updateTime 并持久化
    private fun updateWebBookIfSameDomain(view: WebView?, url: String?, updateTitle: Boolean) {
        if (url == null) return
        val book = webBook ?: return
        val view = view as? MyWebView ?: return
        updateWebBookJob?.cancel()
        updateWebBookJob = lifecycleScope?.launch {
            val urlHost = url.toHttpUrlOrNull()?.topPrivateDomain()
            if (book.url.toHttpUrlOrNull()?.topPrivateDomain() == urlHost) {
                if (book.lastUrl != url) {
                    book.lastUrl = url
                    book.offest = 0
                    book.scrollOffset = 0
                }
                if (updateTitle) {
                    book.lastTitle = view.getTitleFromMeta().ifBlank { view.title ?: "无标题" }
                } else {
                    view.post {
                        if (book.lastUrl == url) {
                            updateWebBookIfSameDomain(view, url, true)
                        }
                    }
                }
                book.updateTime = System.currentTimeMillis()
                vm?.insertWebBook(book)
            }
        }
    }

    private fun createWebViewClient() = object : MyWebViewClient(
        lifecycleScope!!,
        { url, request ->
            Log.i("onAllow isRedirect:${request?.isRedirect} hasGesture:${request?.hasGesture()} isForMainFrame:${request?.isForMainFrame} ${url}")
            loadUrl(url, request)
        },
        { url, request ->
            Log.i("onBlock isRedirect:${request?.isRedirect} hasGesture:${request?.hasGesture()} isForMainFrame:${request?.isForMainFrame} ${url}")
            removeBlankWebView()
        },
        { effectiveCrossOriginMode() }
    ) {

        /**
         * 捕获所有 URL 变化（包括 SPA pushState/replaceState 和 shouldOverrideUrlLoading 返回 true 的情况），
         * 这些场景不会触发 onPageStarted/onPageFinished。
         */
        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            if (url != null) {
                // 在 URL 变化时先记录 url（title 可能尚未可用）
                updateWebBookIfSameDomain(view, url, updateTitle = false)
            }
        }

        override fun onPageFinished(webView: WebView?, url: String?) {
            super.onPageFinished(webView, url)
            // 页面加载完成后再更新 title（如果可用）并持久化
            updateWebBookIfSameDomain(webView, url, updateTitle = true)

            Log.i("onPageFinished ${webView?.currentHost} title:${webView?.title}")
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

        binding.refresh.click {
            val wv = currentWebView ?: return@click
            if (binding.refresh.isSelected) {
                // 当前正在加载 → 停止
                wv.stopLoading()
                binding.refresh.isSelected = false
                return@click
            }
            val url = wv.url ?: return@click
            if (url.isBlank() || url == "about:blank") return@click

            wv.reload()
        }

        binding.backward.click {
            destroyAllWebViews()
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
            val home = webBook?.url ?: globalConfig.bookCityUrl
            // 销毁所有 WebView，重新开始
            destroyAllWebViews()
            loadUrl(home)
        }
        binding.webViewSettings.init(this) {
            crossOriginOverride = -1
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
