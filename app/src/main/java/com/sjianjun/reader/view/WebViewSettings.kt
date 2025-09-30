package com.sjianjun.reader.view

import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.webkit.WebView
import android.widget.FrameLayout
import com.sjianjun.reader.databinding.WebViewSettingsBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.toast
import sjj.alog.Log

class WebViewSettings @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    val binding = WebViewSettingsBinding.inflate(LayoutInflater.from(context), this, true)

    fun bind(webView: WebView) {
        binding.btnCopyUrl.setOnClickListener {
            webView.copyUrlToClipboard()
        }
        binding.btnCopyTitle.setOnClickListener {
            webView.copyTitleToClipboard()
        }
        binding.btnFontAdd.setOnClickListener {
            webView.setFontSize(1)
        }
        binding.btnFontSubtract.setOnClickListener {
            webView.setFontSize(-1)
        }
        binding.menu.setOnClickListener {
            EventBus.post(EventKey.WEB_VIEW_SETTINGS)
        }
        setOnClickListener {
            hide()
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
            toast("已复制到剪贴板：${url}")
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

}