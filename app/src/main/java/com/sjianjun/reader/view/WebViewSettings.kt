package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.widget.addTextChangedListener
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.WebViewSettingsBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.module.bookcity.CustomWebView
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
    }

    fun refresh() {
        val wv = customWebView?.currentWebView ?: return
        // 检查 WebView 是否已被销毁
        if (!wv.isAttachedToWindow || wv.windowToken == null) return
        titleJob?.cancel()
        titleJob = GlobalScope.launch(Dispatchers.Main) {
            val title = wv.getTitleFromMeta()
            if (!title.isNullOrBlank()) {
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
}