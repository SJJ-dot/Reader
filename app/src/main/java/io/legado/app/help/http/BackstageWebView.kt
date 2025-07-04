package io.legado.app.help.http

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.AndroidRuntimeException
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sjianjun.reader.App
import com.sjianjun.reader.WEB_VIEW_UA_ANDROID
import com.sjianjun.reader.utils.gson
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
    private val headerMap: Map<String, String>? = null,
    private val javaScript: String = "document.documentElement.outerHTML",
    private val timeout: Long = 20000L,
) {

    private val mHandler = Handler(Looper.getMainLooper())
    private var callback: Callback? = null
    private var mWebView: WebView? = null

    suspend fun getResponse(): String = withTimeout(timeout) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                launch(Dispatchers.Main) {
                    destroy()
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
                    load()
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }

    @Throws(AndroidRuntimeException::class)
    private fun load() {
        val webView = createWebView()
        mWebView = webView
        try {
            when {
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
    private fun createWebView(): WebView {
        val webView = WebView(App.app)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.blockNetworkImage = true
        settings.userAgentString = headerMap?.get("User-Agent") ?: WEB_VIEW_UA_ANDROID
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.webViewClient = HtmlWebViewClient()
        return webView
    }

    private fun destroy() {
        mWebView?.destroy()
        mWebView = null
    }

    private inner class HtmlWebViewClient : WebViewClient() {

        private var runnable: EvalJsRunnable? = null
        private var isRedirect = false

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            Log.e("==========>: shouldOverrideUrlLoading" + request.url)
            isRedirect = isRedirect || request.isRedirect
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            Log.e("==========>: shouldInterceptRequest" + request?.url)
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView, url: String) {
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
                mWebView.get()?.evaluateJavascript(mJavaScript) {
                    handleResult(it)
                }
            }

            private fun handleResult(result: String) = GlobalScope.launch {
                if (!result.isBlank() && result != "null") {
                    val content = StringEscapeUtils.unescapeJson(result)
                        .replace(quoteRegex, "")
                    try {
                        callback?.onResult(WebViewResponse(url, content))
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

        }

    }

    companion object {
        private val quoteRegex = "^\"|\"$".toRegex()
    }

    abstract class Callback {
        abstract fun onResult(response: WebViewResponse)
        abstract fun onError(error: Throwable)
    }

    class WebViewResponse(url: String, val html: String)
}