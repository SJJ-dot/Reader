package com.sjianjun.reader.view

import android.content.ClipboardManager
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
import com.sjianjun.reader.databinding.WebViewSettingsBinding
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.module.bookcity.AdBlock
import com.sjianjun.reader.module.bookcity.HostStr
import com.sjianjun.reader.module.bookcity.contains
import com.sjianjun.reader.utils.color
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
    private var webView: WebView? = null
    private var titleJob: Job? = null

    init {
        setBackgroundColor(R.color.translucent.color(context))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        titleJob?.cancel()
    }

    fun bind(webView: WebView) {
        this.webView = webView
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

    private fun String?.copyToClipboard() {
        val str = this ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = android.content.ClipData.newPlainText("text", str)
        clipboard?.setPrimaryClip(clipData)
        toast("已复制：${str}")
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
     * 显示拦截提示
     */
    fun showBlockDialog(view: WebView?, request: WebResourceRequest, adBlock: AdBlock?): Boolean {
        val url = request.url.toString()
//        Log.i("shouldOverrideUrlLoading:$url ")
        if (url.endsWith(".apk")) {
            return true
        }
        val targetTop = url.toHttpUrlOrNull()?.topPrivateDomain()
        // 若为广告黑名单，直接拦截
        if (targetTop != null && adBlock?.blacklist.contains(targetTop)) {
            return true
        }

        // 仅对 http/https 链接处理，其他 scheme 拦截
        val scheme = request.url?.scheme
        if (scheme != "http" && scheme != "https") {
            return true
        }

        // 若目标域与当前页主域不同，弹窗询问用户是否加载或加入黑名单
        val currentTop = view?.url?.toHttpUrlOrNull()?.topPrivateDomain()
        val differentSite = when {
            currentTop == null || targetTop == null -> true
            else -> currentTop != targetTop
        }

        if (!differentSite) {
            // 同主域，允许加载
            return false
        }

        // 弹窗询问：拦截并加入黑名单 / 继续加载
        try {
            MaterialAlertDialogBuilder(context)
                .setTitle("外部链接")
                .setMessage(url)
                .setPositiveButton("继续加载") { _, _ ->
                    view?.post { view.loadUrl(url) }
                }
                .setNegativeButton("加入黑名单") { dialog, _ ->
                    // 将目标主域加入黑名单（保持现有黑名单实现）
                    if (targetTop != null) {
                        adBlock?.addBlackHost(HostStr(targetTop, listOf()))
                    }
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()
        } catch (t: Throwable) {
            Log.e("show confirm dialog error", t)
            // 若弹窗失败，默认允许加载
            return false
        }
        // 返回 true 表示我们处理了该事件（等待用户选择）
        return true
    }
}