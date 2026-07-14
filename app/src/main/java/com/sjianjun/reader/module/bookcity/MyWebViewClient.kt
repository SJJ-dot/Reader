package com.sjianjun.reader.module.bookcity

import android.graphics.Bitmap
import android.net.http.SslError
import android.view.LayoutInflater
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.databinding.DialogCrossOriginBinding
import com.sjianjun.reader.preferences.globalConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import java.io.ByteArrayInputStream
import kotlin.time.Duration.Companion.milliseconds

open class MyWebViewClient(
    val lifecycleScope: CoroutineScope,
    val onAllow: (url: String, request: WebResourceRequest?) -> Unit,
    val onBlock: (url: String, request: WebResourceRequest?) -> Unit = { _, _ -> },
    val crossOriginMode: () -> Int = { globalConfig.crossOriginMode },
) : WebViewClient() {

    /**
     * 捕获所有 URL 变化（包括 SPA pushState/replaceState 和 shouldOverrideUrlLoading 返回 true 的情况），
     * 这些场景不会触发 onPageStarted/onPageFinished。
     */
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
//        Log.i("doUpdateVisitedHistory: $url isReload=$isReload ${view?.title}")
        if (url != null) {
            view?.currentHost = url
            // 在 URL 变化时先记录 url（title 可能尚未可用）
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest,
    ): Boolean {
        val showBlockDialog = showBlockDialog(view, request)
        if (!showBlockDialog) {
            val url = request.url.toString()
            view?.currentHost = url
            // 放行后交给 WebView 原生导航，避免手动 loadUrl 导致重定向历史异常。
            return false
        }
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.currentHost = url
        Log.i("onPageStarted:${view?.currentHost} ")
        view?.isBlank = url.isNullOrBlank() || url == "about:blank"
        AdBlock.markPage(url)
    }

    override fun onPageFinished(webView: WebView?, url: String?) {
        webView?.isBlank = url.isNullOrBlank() || url == "about:blank"
        if (webView?.isBlank == false) {
            lifecycleScope?.launch {
                delay(100.milliseconds)
                webView.evaluateJavascript(
                    "(function(){var t=document.body?document.body.innerText.trim():'';return t;})()"
                ) { result ->
                    webView.isBlank = result.length < 10
                    Log.i("js ${webView?.currentHost} isBlank:${webView.isBlank} ${url}")
                }
            }
        } else {
            Log.i("aa ${webView?.currentHost} isBlank:${webView?.isBlank} ${url}")
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        // 电脑版模式：将站点常用的"移动端跳转脚本" uaredirect.js 替换为空实现，
        // 阻止页面 JS 检测到 screen.width<500 后跳转到 m. 站（改 UA 无法绕过该检测）。
        if (globalConfig.webUaMode == 1 && isMobileRedirectScript(request)) {
            Log.i("电脑版：替换移动端跳转脚本 ${request.url}")
            return mobileRedirectScriptStub()
        }
        val requestHost = request.url.toString().toHttpUrlOrNull()?.topPrivateDomain()
        if (requestHost != null && AdBlock.whitelist.contains(requestHost)) {
            return super.shouldInterceptRequest(view, request)
        }

        val currentHost = view?.currentHost
        val isCrossOrigin = requestHost != null && currentHost != null && requestHost != currentHost
        val blocked = AdBlock.blacklist.contains(requestHost) || (isCrossOrigin && crossOriginMode() == 2)

        if (blocked) {
            Log.i("拦截请求：${request.url} mainFrame=${request.isForMainFrame}")
            if (request.isForMainFrame) {
                // 主框架被拦截时不能返回空内容（会变成空白页且 URL 已变），
                // 返回一段立即回退的 HTML，把页面恢复到上一页。
                return mainFrameBlockedResponse()
            }
            if (isCrossOrigin) {
                AdBlock.addUrl(request.url.toString())
            }
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream("".toByteArray())
            )
        }

        AdBlock.addUrl(request.url.toString())
        return super.shouldInterceptRequest(view, request)
    }

    /** 电脑版模式下判断是否为站点常用的"移动端跳转脚本"（uaredirect.js）。 */
    private fun isMobileRedirectScript(request: WebResourceRequest): Boolean {
        val path = request.url.path ?: return false
        return path.substringAfterLast('/').equals("uaredirect.js", ignoreCase = true)
    }

    /** 返回空的 uaredirect.js，使页面的 uaredirect(...) 调用变为空操作，不发生跳转。 */
    private fun mobileRedirectScriptStub(): WebResourceResponse {
        val js = "function uaredirect(){};function isSubdomain(){};"
        return WebResourceResponse("text/javascript", "UTF-8", ByteArrayInputStream(js.toByteArray()))
    }

    private fun mainFrameBlockedResponse(): WebResourceResponse {
        val html = """<!DOCTYPE html><html><head><meta charset="utf-8">
            |<title>已拦截</title></head><body>
            |<script>if(history.length>1){history.back();}else{document.body.innerText='已拦截';}</script>
            |</body></html>""".trimMargin()
        return WebResourceResponse(
            "text/html",
            "UTF-8",
            ByteArrayInputStream(html.toByteArray())
        )
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?,
    ) {
        Log.e("SSL Error: ${error?.toString()}")
        handler?.proceed()
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        Log.e("HTTP Error: ${errorResponse.statusCode} ${request.url}")
    }


    /**
     * 显示拦截提示。根据跨域配置决定行为：
     * - 允许：直接放行
     * - 拒绝：直接拦截
     * - 询问：弹窗让用户选择（允许一次 / 加入黑名单 / 加入白名单）
     */
    fun showBlockDialog(view: WebView?, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (url.endsWith(".apk")) {
            return true
        }
        val targetTop = url.toHttpUrlOrNull()?.topPrivateDomain()

        // 白名单域名直接放行
        if (targetTop != null && AdBlock?.whitelist.contains(targetTop)) {
            return false
        }

        // 若为广告黑名单，直接拦截
        if (targetTop != null && AdBlock?.blacklist.contains(targetTop)) {
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

        // 若目标域与当前页主域不同，根据跨域配置处理
        val currentHost = view?.currentHost
        val differentSite = when {
            currentHost == null || targetTop == null -> true
            else -> currentHost != targetTop
        }

        if (!differentSite) {
            // 同主域，允许加载
            return false
        }

        // 根据跨域配置决定行为
        return when (crossOriginMode()) {
            1 -> false
            2 -> true
            else -> {
                // 询问：延迟弹窗，避免阻塞 shouldOverrideUrlLoading 导致 WebView 卡死
                lifecycleScope?.launch {
                    Log.i("跨域请求：from ${currentHost} to ${targetTop} ${request.url}")
                    showCrossOriginDialog(view, request)
                }
                true
            }
        }
    }

    /**
     * 显示跨域询问弹窗：允许一次 / +黑名单 / +白名单
     */
    private fun showCrossOriginDialog(view: WebView?, request: WebResourceRequest): Boolean {
        try {
            val context = view?.context ?: return false
            val url = request.url.toString()
            val targetTop = url.toHttpUrlOrNull()?.topPrivateDomain()
            val originBinding = DialogCrossOriginBinding.inflate(LayoutInflater.from(context), null, false)
            originBinding.tvDialogUrl.text = targetTop

            val dialog = MaterialAlertDialogBuilder(context)
                .setView(originBinding.root)
                .setCancelable(true)
                .setOnCancelListener {
                    Log.i("跨域请求取消：${url}")
                    onBlock(url, request)
                }
                .create()

            originBinding.btnAllowOnce.setOnClickListener {
                Log.i("跨域请求允许一次：${url}")
                onAllow(url, request)
                dialog.dismiss()
            }
            originBinding.btnWhitelist.setOnClickListener {
                if (targetTop != null) {
                    AdBlock.addWhiteHost(HostStr(targetTop, listOf()))
                }
                Log.i("跨域请求加入白名单：${targetTop}")
                onAllow(url, request)
                dialog.dismiss()
            }
            originBinding.btnBlacklist.setOnClickListener {
                if (targetTop != null) {
                    AdBlock.addBlackHost(HostStr(targetTop, listOf()))
                }
                dialog.dismiss()
                Log.i("跨域请求加入黑名单：${targetTop}")
                onBlock(url, request)
            }

            dialog.show()
        } catch (t: Throwable) {
            Log.e("show confirm dialog error", t)
            return false
        }
        return true
    }
}
