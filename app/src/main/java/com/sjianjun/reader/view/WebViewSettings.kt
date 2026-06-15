package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView

import android.widget.FrameLayout
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.DialogCrossOriginBinding
import com.sjianjun.reader.databinding.WebViewSettingsBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.module.bookcity.AdBlock
import com.sjianjun.reader.module.bookcity.HostStr
import com.sjianjun.reader.module.bookcity.contains
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
import sjj.alog.Log
import kotlin.coroutines.resume
import kotlin.text.contains

class WebViewSettings @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    val binding = WebViewSettingsBinding.inflate(LayoutInflater.from(context), this, true)
    private var webView: WebView? = null
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

    fun bind(webView: WebView, crossOriginMode: Int, onCrossOriginModeChanged: () -> Unit) {
        this.webView = webView
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
                webView.loadUrl(url)
                hide()
                //隐藏输入法
                binding.urlInput.hideKeyboard()
            }
        }
        binding.btnCopyTitle.setOnClickListener {
            binding.webTitle.text.toString().trim().copyToClipboard()
        }
        binding.btnFontAdd.setOnClickListener {
            webView.setFontSize(2)
            binding.tvFontSizeValue.text = webView.settings.textZoom.toString()
        }
        binding.btnFontSubtract.setOnClickListener {
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
            webView.setFontSize(-(webView.settings.textZoom - 100))
            binding.tvFontSizeValue.text = webView.settings.textZoom.toString()
        }
        binding.urlInput.addTextChangedListener {
            if (webView.url == it.toString()) {
                binding.btnCopyUrl.text = "复制"
            } else {
                binding.btnCopyUrl.text = "跳转"
            }
        }

        // 跨域配置 RadioGroup
        when (crossOriginMode) {
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
    }

    fun refresh() {
        titleJob?.cancel()
        titleJob = GlobalScope.launch(Dispatchers.Main) {
            val title = webView?.getTitleFromMeta()
            if (!title.isNullOrBlank()) {
                binding.webTitle.text = title
            } else {
                binding.webTitle.text = webView?.title ?: "~"
            }
        }
        binding.urlInput.setText(webView?.url ?: "")
    }

    private fun WebView.setFontSize(i: Int) {
        settings.apply {
            textZoom = textZoom + i
        }
    }

    private suspend fun WebView.getTitleFromMeta(): String {
        return suspendCancellableCoroutine { continuation ->
            evaluateJavascript(
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
                if (continuation.isActive) {
                    val str = it?.replace("\"", "")?.split(",")?.first()
                    if (str.isNullOrBlank() || str == "null") {
                        continuation.resume("")
                    } else {
                        continuation.resume(str)
                    }
                }

            }

        }
    }

    /**
     * 显示拦截提示。根据跨域配置决定行为：
     * - 允许：直接放行
     * - 拒绝：直接拦截
     * - 询问：弹窗让用户选择（允许一次 / 加入黑名单 / 加入白名单）
     */
    fun showBlockDialog(view: WebView?, request: WebResourceRequest, adBlock: AdBlock?, crossOriginMode: Int): Boolean {
        val url = request.url.toString()
        if (url.endsWith(".apk")) {
            return true
        }
        val targetTop = url.toHttpUrlOrNull()?.topPrivateDomain()

        // 白名单域名直接放行
        if (targetTop != null && adBlock?.whitelist.contains(targetTop)) {
            return false
        }

        // 若为广告黑名单，直接拦截
        if (targetTop != null && adBlock?.blacklist.contains(targetTop)) {
            return true
        }

        // 仅对 http/https 链接处理，其他 scheme 拦截
        val scheme = request.url?.scheme
        if (scheme != "http" && scheme != "https") {
            // 浏览器内部 scheme 放行
            if (scheme in setOf("javascript", "about", "data", "blob", "file")) {
                return false
            }
            // 其他 scheme（intent://, market://, tel:, mailto: 等）拦截
            return true
        }
        if (!request.isForMainFrame) {
            return false
        }
        if (request.hasGesture()) {
            return false
        }

        // 若目标域与当前页主域不同，根据跨域配置处理
        val currentUrl = view?.url
        val currentTop = currentUrl?.toHttpUrlOrNull()?.topPrivateDomain()
        val differentSite = when {
            currentTop == null || targetTop == null -> true
            else -> currentTop != targetTop && !currentUrl.contains(targetTop)
        }

        if (!differentSite) {
            // 同主域，允许加载
            return false
        }

        // 根据跨域配置决定行为
        return when (crossOriginMode) {
            1 -> {
                // 允许：直接放行
                false
            }

            2 -> {
                // 拒绝：直接拦截
                true
            }

            else -> {
                // 询问：延迟弹窗，避免阻塞 shouldOverrideUrlLoading 导致 WebView 卡死
                view?.post { showCrossOriginDialog(view, url, targetTop, adBlock) }
                true
            }
        }
    }

    /**
     * 显示跨域询问弹窗：允许一次 / +黑名单 / +白名单
     */
    private fun showCrossOriginDialog(
        view: WebView?,
        url: String,
        targetTop: String?,
        adBlock: AdBlock?
    ): Boolean {
        try {
            val originBinding = DialogCrossOriginBinding.inflate(LayoutInflater.from(context), null, false)
            originBinding.tvDialogUrl.text = url

            val dialog = MaterialAlertDialogBuilder(context)
                .setView(originBinding.root)
                .setCancelable(true)
                .create()

            originBinding.btnAllowOnce.setOnClickListener {
                view?.post { view.loadUrl(url) }
                dialog.dismiss()
            }
            originBinding.btnWhitelist.setOnClickListener {
                if (targetTop != null) {
                    adBlock?.addWhiteHost(HostStr(targetTop, listOf()))
                }
                view?.post { view.loadUrl(url) }
                dialog.dismiss()
            }
            originBinding.btnBlacklist.setOnClickListener {
                if (targetTop != null) {
                    adBlock?.addBlackHost(HostStr(targetTop, listOf()))
                }
                dialog.dismiss()
            }

            dialog.show()
        } catch (t: Throwable) {
            Log.e("show confirm dialog error", t)
            return false
        }
        return true
    }
}