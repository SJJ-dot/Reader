package com.sjianjun.reader.http

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.AndroidRuntimeException
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.init
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.apache.commons.text.StringEscapeUtils
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 后台webView
 */
class BackstageWebView(
    private val url: String? = null,
    private val headerMap: Map<String, String>? = null,
    private val javaScript: String = "document.documentElement.outerHTML",
    private val timeout: Long = 20000L,
    private val postData: String? = null,
    private val encoding: String? = "UTF-8",
) {

    private val mHandler = Handler(Looper.getMainLooper())
    private var callback: Callback? = null
    private var pooled: WebViewPool.PooledWebView? = null
    @Volatile
    private var cancelled = false

    suspend fun getResponse(): String = withTimeout(timeout) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cancelled = true
                GlobalScope.launch(Dispatchers.Main) {
                    release()
                }
            }
            callback = object : Callback() {
                override fun onResult(response: WebViewResponse) {
                    if (continuation.isActive) {
                        continuation.resume(gson.toJson(response))
                    }
                }

                override fun onError(error: Throwable) {
                    if (continuation.isActive)
                        continuation.resumeWithException(error)
                }
            }
            launch(Dispatchers.Main) {
                try {
                    if (!cancelled) load()
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }

    @Throws(AndroidRuntimeException::class)
    private suspend fun load() {
        val pw = WebViewPool.acquire()
        pooled = pw
        if (cancelled) {
            pooled = null
            pw.release()
            return
        }
        val webView = pw.webView
        try {
            webView.init(headerMap)
            webView.webViewClient = HtmlWebViewClient()
            when {
                postData != null -> webView.postUrl(url!!, postData.toByteArray(charset(encoding?.uppercase() ?: "UTF-8")))
                headerMap == null -> webView.loadUrl(url!!)
                else -> webView.loadUrl(url!!, headerMap)
            }
        } catch (e: Exception) {
            pooled = null
            pw.invalidate()
            callback?.onError(e)
        }
    }

    private fun release() {
        val pw = pooled ?: return
        pooled = null
        val webView = pw.webView
        (webView.webViewClient as? HtmlWebViewClient)?.cancel()
        try {
            webView.stopLoading()
        } catch (_: Exception) {
        }
        pw.release()
    }

    private inner class HtmlWebViewClient : WebViewClient() {

        private var runnable: EvalJsRunnable? = null
        private var isRedirect = false
        @Volatile
        private var disposed = false

        fun cancel() {
            disposed = true
            runnable?.let { mHandler.removeCallbacks(it) }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            isRedirect = isRedirect || request.isRedirect
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (disposed) return
            // 复用 WebView：清掉历史，避免多次请求间回退历史无限增长
            view.clearHistory()
            if (runnable == null) {
                runnable = EvalJsRunnable(view, url, javaScript)
            }
            mHandler.removeCallbacks(runnable!!)
            mHandler.postDelayed(runnable!!, 1000)
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
                if (disposed) return
                mWebView.get()?.evaluateJavascript(mJavaScript) {
                    handleResult(it)
                }
            }

            private fun handleResult(result: String) = GlobalScope.launch {
                if (disposed) return@launch
                if (!result.isBlank() && result != "null") {
                    val content = StringEscapeUtils.unescapeJson(result)
                        .replace(quoteRegex, "")
                    try {
                        callback?.onResult(WebViewResponse(url, content))
                    } catch (e: Exception) {
                        callback?.onError(e)
                    }
                    mHandler.post {
                        release()
                    }
                    return@launch
                }
                if (retry > 30) {
                    callback?.onError(Exception("js执行超时"))
                    mHandler.post {
                        release()
                    }
                    return@launch
                }
                retry++
                mHandler.postDelayed(this@EvalJsRunnable, 1000)
            }

        }

    }

    companion object {
        private val quoteRegex = "^\"|\"$".toRegex()
    }

    abstract class Callback {
        abstract fun onResult(response: WebViewResponse)
        abstract fun onError(error: Throwable)
    }

    class WebViewResponse(val url: String, val html: String)
}