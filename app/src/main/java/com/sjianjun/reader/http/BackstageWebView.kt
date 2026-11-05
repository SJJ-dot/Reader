package com.sjianjun.reader.http

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.AndroidRuntimeException
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sjianjun.reader.module.verification.WebViewVerificationActivity
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.utils.init
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.apache.commons.text.StringEscapeUtils
import sjj.alog.Log
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 后台 WebView：一次网络请求加载页面；若命中人机验证页，把**同一个 WebView 实例**提升到前台
 * 由人工处理，完成后再回传最终页面内容——全程不重新 loadUrl，脚本的那次请求也不会中断。
 */
class BackstageWebView(
    private val url: String? = null,
    private val headerMap: Map<String, String>? = null,
    private val javaScript: String = "document.documentElement.outerHTML",
    private val timeout: Long = 20000L,
    private val postData: String? = null,
    private val encoding: String? = "UTF-8",
    private val autoHumanVerify: Boolean = true,
    private val verificationKey: String = "",
) {

    private val mHandler = Handler(Looper.getMainLooper())
    private var callback: Callback? = null
    private var pooled: WebViewPool.PooledWebView? = null

    @Volatile
    private var cancelled = false

    /**
     * 已提升为前台人工验证。期间：不再超时、不再自动抓取页面（避免页面跳转时重复回调），
     * 结束时销毁 WebView 而非归还池复用。
     */
    @Volatile
    private var handoffActive = false

    suspend fun getResponse(): String = suspendCancellableCoroutine { continuation ->
        // 超时只约束"后台加载阶段"。不能用 withTimeout：它会取消协程并触发
        // invokeOnCancellation -> release()，把正在前台供用户操作的 WebView 清成 about:blank。
        val timeoutTask = Runnable {
            if (continuation.isActive && !handoffActive) {
                cancelled = true
                mHandler.post { release() }
                continuation.resumeWithException(Exception("后台 WebView 加载超时"))
            }
        }
        mHandler.postDelayed(timeoutTask, timeout)

        continuation.invokeOnCancellation {
            cancelled = true
            mHandler.removeCallbacks(timeoutTask)
            GlobalScope.launch(Dispatchers.Main) {
                release()
            }
        }
        callback = object : Callback() {
            override fun onResult(response: WebViewResponse) {
                mHandler.removeCallbacks(timeoutTask)
                if (continuation.isActive) {
                    continuation.resume(gson.toJson(response))
                }
            }

            override fun onError(error: Throwable) {
                mHandler.removeCallbacks(timeoutTask)
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        }
        GlobalScope.launch(Dispatchers.Main) {
            try {
                if (!cancelled) load()
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
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

    /** 必须在主线程调用。 */
    private fun release() {
        val pw = pooled ?: return
        pooled = null
        val webView = pw.webView
        (webView.webViewClient as? HtmlWebViewClient)?.cancel()
        try {
            webView.stopLoading()
        } catch (_: Exception) {
        }
        if (handoffActive) {
            // 人工验证用过的 WebView 不归还池：归还池会 loadUrl("about:blank") 清空页面，
            // 且其前台附着状态 / 验证会话也不适合被后续请求复用，直接销毁最干净。
            (webView.parent as? ViewGroup)?.removeView(webView)
            pw.invalidate()
        } else {
            pw.release()
        }
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
            // 提升到前台后用户操作引发的页面跳转不再触发抓取，避免重复回调 / 提前结束脚本请求
            if (disposed || handoffActive) return
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
                if (disposed || handoffActive) return
                mWebView.get()?.evaluateJavascript(mJavaScript) {
                    handleResult(it)
                }
            }

            private fun handleResult(result: String) = GlobalScope.launch {
                if (disposed || handoffActive) return@launch
                if (!result.isBlank() && result != "null") {
                    val content = StringEscapeUtils.unescapeJson(result)
                        .replace(quoteRegex, "")
                    if (autoHumanVerify && isHumanVerificationPage(content, url)) {
                        // 命中人机验证：把当前已加载好的 WebView 交给前台，阻塞等待用户处理完成，
                        // 期间脚本的这一次 HTTP 请求一直挂起，不发起第二次网络请求。
                        handoffActive = true
                        mHandler.removeCallbacks(this@EvalJsRunnable)
                        val wv = mWebView.get()
                        val finalContent = if (wv != null) {
                            Log.i("web_get 命中人机验证页，提升当前 WebView 到前台：$url")
                            WebViewVerificationActivity.showHandoffAndWaitHtml(
                                wv, url, verificationKey
                            )
                        } else {
                            null
                        }
                        try {
                            // 前台被脚本取消 / 活动异常结束时可能拿不到内容，回退到原验证页 HTML，
                            // 避免把空串当成结果返回给脚本。
                            callback?.onResult(
                                WebViewResponse(url, finalContent?.takeIf { it.isNotBlank() } ?: content)
                            )
                        } catch (e: Exception) {
                            callback?.onError(e)
                        }
                        mHandler.post { release() }
                        return@launch
                    }
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

/**
 * 粗略判定一个已加载的页面是否为人机验证 / 反爬拦截页。
 * 命中常见验证码服务商（reCAPTCHA / hCaptcha / Cloudflare / DataDome 等）或典型拦截文案
 * （如 Cloudflare "Just a moment"、各类"安全验证/机器人检查"）即视为需要人工介入。
 */
private fun isHumanVerificationPage(html: String, url: String?): Boolean {
    val lowUrl = url?.lowercase().orEmpty()
    if (lowUrl.contains("captcha") || lowUrl.contains("verify") || lowUrl.contains("challenge") ||
        lowUrl.contains("recaptcha") || lowUrl.contains("hcaptcha") || lowUrl.contains("turnstile") ||
        lowUrl.contains("cloudflare") || lowUrl.contains("robot") || lowUrl.contains("ddos") ||
        lowUrl.contains("antibot") || lowUrl.contains("waf")
    ) {
        return true
    }
    val low = html.lowercase()
    val markers = arrayOf(
        "recaptcha", "grecaptcha", "hcaptcha", "cf-mitigated", "cf-chl", "jschl",
        "just a moment", "checking your browser", "verify you are human",
        "verify you are a human", "are you a robot", "robot check", "人机验证",
        "安全验证", "请完成安全验证", "complete the security check", "attention required",
        "enable javascript and cookies", "ddos protection", "datadome",
        "px-captcha", "perimeterx", "incapsula", "unusual traffic"
    )
    return markers.any { it in low }
}
