package io.legado.app.help.http

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.AndroidRuntimeException
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.sjianjun.reader.App
import com.sjianjun.reader.WEB_VIEW_UA_ANDROID
import io.legado.app.exception.NoStackTraceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.apache.commons.text.StringEscapeUtils
import sjj.alog.Log
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 后台webView
 */
class BackstageWebView(
    private val url: String? = null,
    private val html: String? = null,
    private val encode: String? = null,
    private val tag: String? = null,
    private val headerMap: Map<String, String>? = null,
    private val sourceRegex: String? = null,
    private val overrideUrlRegex: String? = null,
    private val javaScript: String? = null,
    private val delayTime: Long = 0,
) {

    private val mHandler = Handler(Looper.getMainLooper())
    private var callback: Callback? = null
    private var mWebView: WebView? = null

    suspend fun geResponse(act: AppCompatActivity): String = withTimeout(10000L) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                launch(Dispatchers.Main) {
                    destroy()
                }
            }
            callback = object : Callback() {
                override fun onResult(response: String) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    }
                }

                override fun onError(error: Throwable) {
                    if (continuation.isActive)
                        continuation.resumeWithException(error)
                }
            }
            launch(Dispatchers.Main) {
                try {
                    load(act)
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }

    private fun getEncoding(): String {
        return encode ?: "utf-8"
    }

    @Throws(AndroidRuntimeException::class)
    private fun load(act: AppCompatActivity) {
        val webView = createWebView(act)
        mWebView = webView
        try {
            when {
                !html.isNullOrEmpty() -> if (url.isNullOrEmpty()) {
                    webView.loadData(html, "text/html", getEncoding())
                } else {
                    webView.loadDataWithBaseURL(url, html, "text/html", getEncoding(), url)
                }

                else -> if (headerMap == null) {
                    webView.loadUrl(url!!)
                } else {
                    webView.loadUrl(url!!, headerMap)
                }
            }
        } catch (e: Exception) {
            callback?.onError(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(act: AppCompatActivity): WebView {
        val webView = WebView(act)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.blockNetworkImage = true
        settings.userAgentString = headerMap?.get("User-Agent") ?: WEB_VIEW_UA_ANDROID
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        if (sourceRegex.isNullOrBlank() && overrideUrlRegex.isNullOrBlank()) {
            webView.webViewClient = HtmlWebViewClient()
        } else {
            webView.webViewClient = SnifferWebClient()
        }
        return webView
    }

    private fun destroy() {
        mWebView?.destroy()
        mWebView = null
    }

    private fun getJs(): String {
        javaScript?.let {
            if (it.isNotEmpty()) {
                return it
            }
        }
        return JS
    }

    private inner class HtmlWebViewClient : WebViewClient() {

        private var runnable: EvalJsRunnable? = null
        private var isRedirect = false

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            isRedirect = isRedirect || request.isRedirect
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (runnable == null) {
                runnable = EvalJsRunnable(view, url, getJs())
            }
            mHandler.removeCallbacks(runnable!!)
            mHandler.postDelayed(runnable!!, 1000 + delayTime)
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            handler?.proceed()
        }

        private inner class EvalJsRunnable(
            webView: WebView,
            private val url: String,
            private val mJavaScript: String
        ) : Runnable {
            var retry = 0
            private val mWebView: WeakReference<WebView> = WeakReference(webView)
            override fun run() {
                mWebView.get()?.evaluateJavascript(mJavaScript) {
                    Log.e("evaluateJavascript")
                    handleResult(it)
                }
            }

            private fun handleResult(result: String) = GlobalScope.launch {
                if (result.isNotEmpty() && result != "null") {
                    val content = StringEscapeUtils.unescapeJson(result)
                        .replace(quoteRegex, "")
                    try {
                        val response = buildStrResponse(content)
                        callback?.onResult(response)
                    } catch (e: Exception) {
                        callback?.onError(e)
                    }
                    mHandler.post {
                        destroy()
                    }
                    return@launch
                }
                if (retry > 30) {
                    callback?.onError(NoStackTraceException("js执行超时"))
                    mHandler.post {
                        destroy()
                    }
                    return@launch
                }
                retry++
                mHandler.postDelayed(this@EvalJsRunnable, 1000)
            }

            private fun buildStrResponse(content: String): String {
                if (!isRedirect) {
                    return content
                }
                return content
            }
        }

    }

    private inner class SnifferWebClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            if (shouldOverrideUrlLoading(request.url.toString())) {
                return true
            }
            return super.shouldOverrideUrlLoading(view, request)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "KotlinRedundantDiagnosticSuppress")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (shouldOverrideUrlLoading(url)) {
                return true
            }
            return super.shouldOverrideUrlLoading(view, url)
        }

        private fun shouldOverrideUrlLoading(requestUrl: String): Boolean {
            overrideUrlRegex?.let {
                if (requestUrl.matches(it.toRegex())) {
                    try {
                        val response = requestUrl
                        callback?.onResult(response)
                    } catch (e: Exception) {
                        callback?.onError(e)
                    }
                    destroy()
                    return true
                }
            }
            return false
        }

        override fun onLoadResource(view: WebView, resUrl: String) {
            sourceRegex?.let {
                if (resUrl.matches(it.toRegex())) {
                    try {
                        val response = resUrl
                        callback?.onResult(response)
                    } catch (e: Exception) {
                        callback?.onError(e)
                    }
                    destroy()
                }
            }
        }

        override fun onPageFinished(webView: WebView, url: String) {
            if (!javaScript.isNullOrEmpty()) {
                val runnable = LoadJsRunnable(webView, javaScript)
                mHandler.postDelayed(runnable, 1000L + delayTime)
            }
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            handler?.proceed()
        }

        private inner class LoadJsRunnable(
            webView: WebView,
            private val mJavaScript: String?
        ) : Runnable {
            private val mWebView: WeakReference<WebView> = WeakReference(webView)
            override fun run() {
                mWebView.get()?.loadUrl("javascript:${mJavaScript}")
            }
        }

    }

    companion object {
        const val JS = "document.documentElement.outerHTML"
        private val quoteRegex = "^\"|\"$".toRegex()
    }

    abstract class Callback {
        abstract fun onResult(response: String)
        abstract fun onError(error: Throwable)
    }
}