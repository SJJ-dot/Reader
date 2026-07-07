package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
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
import com.sjianjun.reader.preferences.DelegateSharedPref
import com.sjianjun.reader.preferences.dataPref
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.init
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import com.tencent.mmkv.MMKV
import io.legado.app.utils.activity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

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

    /** 单 WebView 实例：所有导航复用它，后退/前进依赖 WebView 原生历史 */
    var webView: MyWebView? = null
        private set
    private val skippedHistoryIndices = linkedMapOf<Int, String>()

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

    fun loadUrl(url: String) {
        Log.i("loadUrl:$url ")
        val existing = webView
        if (existing != null && !existing.isDestroyed) {
            existing.currentHost = url
            existing.loadUrl(url)
            updateForwardButton()
            return
        }
        val wv = MyWebView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            init()
            webViewClient = createWebViewClient()
            webChromeClient = createWebChromeClient()
        }
        skippedHistoryIndices.clear()
        wv.currentHost = url
        binding.webViewContainer.addView(wv)
        webView = wv
        wv.loadUrl(url)
        updateForwardButton()
    }

    fun back(): Boolean {
        val wv = webView ?: return false
        // 跳过空白页（about:blank 等），直接回到最近的非空白页
        val step = wv.meaningfulBackStep()
        if (step < 0) {
            wv.goBackOrForward(step)
            updateForwardButton()
            return true
        }
        return false
    }

    fun goForward(): Boolean {
        val wv = webView ?: return false
        // 跳过空白页，直接前进到最近的非空白页
        val step = wv.meaningfulForwardStep()
        if (step > 0) {
            wv.goBackOrForward(step)
            updateForwardButton()
            return true
        }
        return false
    }

    private fun updateForwardButton() {
        val wv = webView
        binding.forward.isEnabled = wv != null && wv.meaningfulForwardStep() > 0
        binding.refresh.isSelected = (wv?.progress ?: 100) < 100
    }

    /** 返回向后最近的可用页面步数（负值），没有则返回 0。 */
    private fun MyWebView.meaningfulBackStep(): Int {
        val list = copyBackForwardList()
        pruneSkippedHistoryIndices(list.size)
        var idx = list.currentIndex - 1
        var step = -1
        while (idx >= 0) {
            val u = list.getItemAtIndex(idx).url
            if (!isSkippableHistoryItem(idx, u)) return step
            idx--
            step--
        }
        return 0
    }

    /** 返回向前最近的可用页面步数（正值），没有则返回 0。 */
    private fun MyWebView.meaningfulForwardStep(): Int {
        val list = copyBackForwardList()
        pruneSkippedHistoryIndices(list.size)
        var idx = list.currentIndex + 1
        var step = 1
        while (idx < list.size) {
            val u = list.getItemAtIndex(idx).url
            if (!isSkippableHistoryItem(idx, u)) return step
            idx++
            step++
        }
        return 0
    }

    private fun isSkippableHistoryItem(index: Int, url: String?): Boolean {
        if (url.isNullOrBlank() || url == "about:blank") return true
        // 索引可能因新导航被截断/复用，需同时校验 url 一致才认为是被标记跳过的条目
        return skippedHistoryIndices[index] == url
    }

    private fun pruneSkippedHistoryIndices(historySize: Int) {
        val it = skippedHistoryIndices.entries.iterator()
        while (it.hasNext()) {
            if (it.next().key >= historySize) it.remove()
        }
    }

    private fun MyWebView.markCurrentHistoryAsSkipped() {
        val list = copyBackForwardList()
        val idx = list.currentIndex
        if (idx in 0 until list.size) {
            skippedHistoryIndices[idx] = list.getItemAtIndex(idx).url
        }
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
                    book.updateTime = System.currentTimeMillis()
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
                vm?.insertWebBook(book)
            }
        }
    }

    private fun createWebViewClient() = object : MyWebViewClient(
        lifecycleScope!!,
        { url, request ->
            Log.i("onAllow isRedirect:${request?.isRedirect} hasGesture:${request?.hasGesture()} isForMainFrame:${request?.isForMainFrame} $url")
            // 自动重定向（主框架、无用户手势）：标记当前页（重定向源）为跳过，
            // 后退时越过它，避免 后退→重定向源→再次重定向 的死循环。
            // 用户点击（hasGesture）不标记，保留可后退到来源页。
            if (request?.isForMainFrame == true && request?.hasGesture() == false) {
                webView?.markCurrentHistoryAsSkipped()
            }
            loadUrl(url)
        },
        { url, request ->
            Log.i("onBlock isRedirect:${request?.isRedirect} hasGesture:${request?.hasGesture()} isForMainFrame:${request?.isForMainFrame} $url")
            if (request?.isForMainFrame == true && !request.hasGesture()) {
                recoverFromBlockedMainFrame(url)
            }
        },
        { effectiveCrossOriginMode() }
    ) {

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            if (url != null) {
                updateWebBookIfSameDomain(view, url, updateTitle = false)
            }
        }

        override fun onPageFinished(webView: WebView?, url: String?) {
            super.onPageFinished(webView, url)
            binding.searchRefresh.visibility = GONE
            val wv = webView as? MyWebView
            if (wv != null) {
                val target = wv.forceReloadTargetUrl
                if (target != null) {
                    // 强制刷新第一步：空白页加载完成，加载目标 URL（全新导航，绕过 BFCache）
                    wv.markCurrentHistoryAsSkipped() // about:blank
                    wv.loadUrl(target)
                    wv.forceReloadTargetUrl = null
                    return
                }
            }
            // 页面加载完成后再更新 title（如果可用）并持久化
            updateWebBookIfSameDomain(webView, url, updateTitle = true)
            updateForwardButton()

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

    private fun recoverFromBlockedMainFrame(blockedUrl: String) {
        val wv = webView ?: return
        wv.post {
            if (webView !== wv || wv.isDestroyed || wv.isBlank != true) return@post
            wv.markCurrentHistoryAsSkipped()
            val step = wv.meaningfulBackStep()
            if (step < 0) {
                wv.goBackOrForward(step)
                updateForwardButton()
                Log.i("recoverFromBlockedMainFrame step:$step blocked:$blockedUrl")
            }
        }
    }

    private fun createWebChromeClient() = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (view !== webView) return
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
            val wv = webView ?: return@click
            if (binding.refresh.isSelected) {
                wv.stopLoading()
                binding.refresh.isSelected = false
                return@click
            }
            val url = wv.url ?: return@click
            if (url.isBlank() || url == "about:blank") return@click

            wv.reload()
        }
        binding.refresh.setOnLongClickListener {
            forceReload()
            true
        }

        binding.backward.click {
            destroyWebView()
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
            destroyWebView()
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


    private fun destroyWebView() {
        webView?.let { wv ->
            wv.stopLoading()
            wv.settings.javaScriptEnabled = false
            wv.clearHistory()
            wv.removeAllViews()
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
            Log.i("销毁 WebView: ${wv.currentHost}")
        }
        webView = null
        skippedHistoryIndices.clear()

    }

    fun forceReload() {
        val wv = webView ?: run {
            toast("无可刷新的页面")
            return
        }
        val url = wv.url
        if (url.isNullOrBlank() || url == "about:blank") {
            toast("无可刷新的页面")
            return
        }
        wv.markCurrentHistoryAsSkipped() // 强制刷新的当前损坏页
        wv.forceReloadTargetUrl = url
        wv.loadUrl("about:blank") // 先加载空白页，丢弃损坏的页面/渲染状态
        toast("强制刷新")
        Log.i("forceReload $url")
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
