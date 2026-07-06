package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import kotlin.coroutines.resume


var WebView.currentHost: String?
    get() = (this as? MyWebView)?.currentHost
    set(url) {
        if (this is MyWebView) {
            currentHost = url
        }
    }

class MyWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : WebView(context, attrs, defStyleAttr) {
    init {
        // 点击网页内输入框时需要 WebView 可获取焦点并提供输入连接，否则会
        // "Ignoring showSoftInput() as view=...WebView... is not served" 不弹输入法。
        isFocusable = true
        isFocusableInTouchMode = true
    }

    var currentHost: String? = null
        set(url) {
            field = url?.toHttpUrlOrNull()?.topPrivateDomain()
        }

    var isDestroyed = false
        private set

    override fun onCheckIsTextEditor(): Boolean = true

    override fun destroy() {
        super.destroy()
        isDestroyed = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 避免 WebView 内存泄漏
        stopLoading()
        settings.javaScriptEnabled = false
        clearHistory()
        removeAllViews()
        destroy()
    }
}

suspend fun WebView.getTitleFromMeta(): String {
    return suspendCancellableCoroutine { continuation ->
        evaluateJavascript(
            """
                javascript:(function() {
                    return document.title
                })()
            """.trimIndent()
        ) {
            Log.i("title:$it")
            if (continuation.isActive) {
                title
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

