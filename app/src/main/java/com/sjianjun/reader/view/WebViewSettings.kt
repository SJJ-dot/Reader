package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.WebBook
import com.sjianjun.reader.databinding.WebViewSettingsBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.module.bookcity.CustomWebView
import com.sjianjun.reader.module.bookcity.getTitleFromMeta
import com.sjianjun.reader.module.search.SearchViewModel
import com.sjianjun.reader.module.shelf.WebShelfViewModel
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.copyToClipboard
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.hideKeyboard
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import sjj.alog.Log
import kotlin.coroutines.resume

class WebViewSettings @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    val binding = WebViewSettingsBinding.inflate(LayoutInflater.from(context), this, true)
    private var customWebView: CustomWebView? = null
    private var titleJob: Job? = null

    /** 当用户在设置中修改跨域配置时回调，用于清除临时覆盖 */
    var onCrossOriginModeChanged: (() -> Unit)? = null

    init {
        setBackgroundColor(R.color.translucent.color(context))
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) {
            refresh()
        } else {
            titleJob?.cancel()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        titleJob?.cancel()
    }

    fun init(customWebView: CustomWebView, onCrossOriginModeChanged: () -> Unit) {
        this.customWebView = customWebView
        this.onCrossOriginModeChanged = onCrossOriginModeChanged
        binding.btnCopyUrl.setOnClickListener {
            if (binding.btnCopyUrl.text == "复制") {
                binding.urlInput.text.toString().trim().copyToClipboard()
            } else {
                var url = binding.urlInput.text.toString()
                if (!url.startsWith("http")) {
                    url = "http://$url"
                }
                if (url.toHttpUrlOrNull() == null) {
                    toast("请输入正确的网址")
                    return@setOnClickListener
                }
                hide()
                binding.urlInput.hideKeyboard()
                val webView = customWebView.currentWebView ?: return@setOnClickListener
                webView.loadUrl(url)
            }
        }
        binding.btnCopyTitle.setOnClickListener {
            binding.webTitle.text.toString().trim().copyToClipboard()
        }
        binding.btnFontAdd.setOnClickListener {
            val webView = customWebView.currentWebView ?: return@setOnClickListener
            webView.setFontSize(2)
            binding.tvFontSizeValue.text = webView.settings.textZoom.toString()
        }
        binding.btnFontSubtract.setOnClickListener {
            val webView = customWebView.currentWebView ?: return@setOnClickListener
            webView.setFontSize(-2)
            binding.tvFontSizeValue.text = webView.settings.textZoom.toString()
        }
        binding.menu.setOnClickListener {
            EventBus.post(EventKey.WEB_VIEW_SETTINGS)
            hide()
        }
        setOnClickListener {
            hide()
        }
        binding.tvFontSizeValue.setOnClickListener {
            val webView = customWebView.currentWebView ?: return@setOnClickListener
            webView.setFontSize(-(webView.settings.textZoom - 100))
            binding.tvFontSizeValue.text = webView.settings.textZoom.toString()
        }
        binding.urlInput.addTextChangedListener {
            val webView = customWebView.currentWebView ?: return@addTextChangedListener
            if (webView.url == it.toString()) {
                binding.btnCopyUrl.text = "复制"
            } else {
                binding.btnCopyUrl.text = "跳转"
            }
        }

        // 跨域配置 RadioGroup
        when (customWebView.effectiveCrossOriginMode()) {
            0 -> binding.rbAsk.isChecked = true
            1 -> binding.rbAllow.isChecked = true
            2 -> binding.rbDeny.isChecked = true
        }
        binding.rgCrossOrigin.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_ask -> 0
                R.id.rb_allow -> 1
                R.id.rb_deny -> 2
                else -> return@setOnCheckedChangeListener
            }
            globalConfig.crossOriginMode = mode
            onCrossOriginModeChanged?.invoke()
        }

        binding.btnAddToShelf.setOnClickListener {
            addToShelf()
        }

        binding.btnAddToHome.setOnClickListener {
            val wv = customWebView?.currentWebView ?: return@setOnClickListener
            val url = wv.url ?: return@setOnClickListener
            val historyList = globalConfig.bookCityUrlHistoryList.value?.toMutableList() ?: mutableListOf()
            historyList.remove(url)
            historyList.add(0, url)
            globalConfig.bookCityUrlHistoryList.postValue(historyList)
            toast("添加成功")
        }
    }

    private fun addToShelf() {
        val wv = customWebView?.currentWebView ?: return
        val url = wv.url ?: return

        val items = arrayOf(
            Pair("导入书源书架", R.mipmap.ic_shelf),
            Pair("导入网页书架", R.mipmap.ic_browser)
        )

        val adapter = object : ArrayAdapter<Pair<String, Int>>(
            context,
            R.layout.item_import_shelf_type,
            items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_import_shelf_type, parent, false)
                val tvName = view.findViewById<TextView>(R.id.tv_name)
                val ivIcon = view.findViewById<ImageView>(R.id.iv_icon)
                val item = getItem(position)
                tvName.text = item?.first
                item?.second?.let { ivIcon.setImageResource(it) }
                return view
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("选择")
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> importToBookSourceShelf(url)
                    1 -> importToWebShelf(wv, url)
                }
            }
            .show()
    }

    /** 导入书源书架：通过 SearchViewModel.searchUrl 搜索并保存 */
    private fun importToBookSourceShelf(url: String) {
        val scope = customWebView?.lifecycleScope ?: return
        scope.launch {
            try {
                val vm = SearchViewModel()
                val results = vm.searchUrl(url)
                results.collect { groups ->
                    if (groups.isNotEmpty()) {
                        val allResults = groups.flatten()
                        vm.saveSearchResult(allResults)
                        toast("已导入书源书架")
                    } else {
                        toast("导入失败")
                    }
                }
            } catch (e: Exception) {
                Log.e("导入书源书架失败", e)
                toast("导入失败: ${e.message}")
            }
        }
    }

    /** 导入网页书架：直接从 WebView 获取 HTML，解析封面后保存 */
    private fun importToWebShelf(wv: WebView, url: String) {
        val scope = customWebView?.lifecycleScope ?: return
        scope.launch {
            try {
                val html = wv.getHtml() ?: return@launch
                val title = wv.getTitleFromMeta().ifBlank { wv.title ?: "无标题" }
                val webBook = WebBook().apply {
                    this.url = url
                    this.title = title.ifBlank { "未命名" }
                    this.lastUrl = url
                    this.lastTitle = title
                }
                // 从 HTML 解析封面
                val document = Jsoup.parse(html, url)
                var cover = document.select("meta[property='og:image']").firstOrNull()?.attr("content")
                if (cover.isNullOrEmpty()) {
                    cover = document.select("#fmimg").firstOrNull()?.absUrl("src")
                }
                if (cover.isNullOrEmpty()) {
                    cover = document.select("img").firstOrNull {
                        it.attr("alt").contains(title) || it.attr("title").contains(title) || it.attr("src").contains("cover")
                    }?.absUrl("src")
                }
                if (cover.isNullOrEmpty()) {
                    cover = url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}/favicon.ico" }
                }
                webBook.cover = cover

                val vm = WebShelfViewModel()
                vm.insertWebBook(webBook)
                toast("已导入网页书架")
            } catch (e: Exception) {
                Log.e("导入网页书架失败", e)
                toast("导入失败: ${e.message}")
            }
        }
    }

    /** 从 WebView 获取当前页面 HTML */
    private suspend fun WebView.getHtml(): String? {
        return suspendCancellableCoroutine { cont ->
            evaluateJavascript(
                "(function(){return document.documentElement.outerHTML})()"
            ) { result ->
                if (cont.isActive) {
                    val html = result?.trim('"')?.unescapeJava()
                    cont.resume(html)
                }
            }
        }
    }

    /** 处理 evaluateJavascript 返回的转义字符串 */
    private fun String.unescapeJava(): String {
        return this
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
    }

    fun refresh() {
        val wv = customWebView?.currentWebView ?: return
        // 检查 WebView 是否已被销毁
        if (!wv.isAttachedToWindow || wv.windowToken == null) return
        titleJob?.cancel()
        titleJob = GlobalScope.launch(Dispatchers.Main) {
            val title = wv.getTitleFromMeta()
            if (title.isNotBlank()) {
                binding.webTitle.text = title
            } else {
                binding.webTitle.text = wv.title ?: "~"
            }
        }
        binding.urlInput.setText(wv.url ?: "")
    }

    private fun WebView.setFontSize(i: Int) {
        settings.apply {
            textZoom = textZoom + i
        }
    }


}