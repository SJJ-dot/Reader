package com.sjianjun.reader.view

import android.content.ClipboardManager
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
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.hideKeyboard
import com.sjianjun.reader.utils.toast
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

class WebViewSettings @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    val binding = WebViewSettingsBinding.inflate(LayoutInflater.from(context), this, true)
    private var webView: WebView? = null

    init {
        setBackgroundColor(R.color.translucent.color(context))
    }

    fun bind(webView: WebView) {
        this.webView = webView
        binding.btnCopyUrl.setOnClickListener {
            if (binding.btnCopyUrl.text == "复制") {
                webView.copyUrlToClipboard()
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
            webView.copyTitleToClipboard()
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
            if (webView?.url == it.toString()) {
                binding.btnCopyUrl.text = "复制"
            } else {
                binding.btnCopyUrl.text = "跳转"
            }
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            binding.webTitle.text = webView?.title ?: "~"
            binding.urlInput.setText(webView?.url ?: "")
        }
    }

    private fun WebView.setFontSize(i: Int) {
        settings.apply {
            textZoom = textZoom + i
        }
    }

    private fun WebView.copyUrlToClipboard() {
        url?.let { url ->
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = android.content.ClipData.newPlainText("text", url)
            clipboard?.setPrimaryClip(clipData)
            toast("已复制：${url}")
        }
    }

    private fun WebView.copyTitleToClipboard() {
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
            var str = it?.replace("\"", "")?.split(",")?.first()
            if (str.isNullOrBlank() || str == "null") {
//                toast("标题获取失败")
                str = title
                if (str.isNullOrBlank()) {
                    toast("标题获取失败")
                    return@evaluateJavascript
                }
            }
            Log.i("复制到剪贴板:$str")
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = android.content.ClipData.newPlainText("text", str)
            clipboard?.setPrimaryClip(clipData)
            toast("已复制：${str}")
        }
    }

}