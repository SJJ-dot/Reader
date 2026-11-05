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
        adBlockLog { "history url=$url isReload=$isReload" }
        if (url != null) {
            view?.currentHost = url
            // URL 变化即视为“页面成为可见页”，刷新最后显示时间（后退/前进/SPA 也能命中），用于访问记录排序
            AdBlock.touchPage(url)
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url.toString()
        adBlockLog { "override 入口 url=$url isForMainFrame=${request.isForMainFrame}" }
        val showBlockDialog = showBlockDialog(view, request)
        adBlockLog { "override 结果 url=$url intercept=$showBlockDialog" }
        if (!showBlockDialog) {
            view?.currentHost = url
            // 放行后交给 WebView 原生导航，避免手动 loadUrl 导致重定向历史异常。
            return false
        }
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.currentHost = url
        adBlockLog { "pageStarted url=$url currentHost=${view?.currentHost}" }
        view?.isBlank = url.isNullOrBlank() || url == "about:blank"
        AdBlock.markPage(url)
    }

    override fun onPageFinished(webView: WebView?, url: String?) {
        AdBlock.finishPage(url)
        webView?.isBlank = url.isNullOrBlank() || url == "about:blank"
        if (!url.isNullOrBlank() && webView?.isBlank == false) {
            AdBlock.updatePageMeta(url, webView.title)
        }
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
        val requestUrl = request.url.toString()
        // 按「精确URL > 子域名 > 顶级域名」判定一次，后续拦/放与记录都复用这个结果
        val hit = AdBlock.resolve(requestUrl)
        adBlockLog { "intercept 入口 url=$requestUrl mainFrame=${request.isForMainFrame} hit=${hit?.kind}:${hit?.rule?.host}:${hit?.rule?.mode} allowed=${hit?.allowed}" }
        if (hit?.allowed == true) {
            adBlockLog { "intercept 放行规则→不记录 url=$requestUrl" }
            return super.shouldInterceptRequest(view, request)
        }

        val requestHost = requestUrl.toHttpUrlOrNull()?.topPrivateDomain()
        val currentHost = view?.currentHost
        val isCrossOrigin = requestHost != null && currentHost != null && requestHost != currentHost
        val blocked = (hit != null && !hit.allowed) || (isCrossOrigin && crossOriginMode() == 2)
        adBlockLog { "intercept 判定 url=$requestUrl blocked=$blocked isCrossOrigin=$isCrossOrigin crossMode=${crossOriginMode()} currentHost=$currentHost requestHost=$requestHost" }

        if (blocked) {
            adBlockLog { "intercept 拦截分支→addUrl(true) url=$requestUrl mainFrame=${request.isForMainFrame}" }
            // 被拦截的请求同样计入访问记录（含同域、跨域与主框架），便于查看拦截效果与误拦恢复
            AdBlock.addUrl(requestUrl, true)
            if (request.isForMainFrame) {
                adBlockLog { "intercept 主框架被拦→返回回退HTML url=$requestUrl" }
                // 主框架被拦截时不能返回空内容（会变成空白页且 URL 已变），
                // 返回一段立即回退的 HTML，把页面恢复到上一页。
                return mainFrameBlockedResponse()
            }
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream("".toByteArray())
            )
        }

        adBlockLog { "intercept 正常分支→addUrl(false) url=$requestUrl" }
        AdBlock.addUrl(requestUrl, false)
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
        adBlockLog { "showBlockDialog 入口 url=$url" }
        if (url.endsWith(".apk")) {
            adBlockLog { "showBlockDialog .apk→拦截 url=$url" }
            return true
        }
        val targetTop = url.toHttpUrlOrNull()?.topPrivateDomain()

        // 按层级优先级判定一次：放行规则直接放行，拦截规则直接拦截
        val hit = AdBlock.resolve(url)
        adBlockLog { "showBlockDialog 规则判定 url=$url hit=${hit?.kind}:${hit?.rule?.host}:${hit?.rule?.mode} allowed=${hit?.allowed}" }
        if (hit?.allowed == true) {
            adBlockLog { "showBlockDialog 放行规则→return false url=$url" }
            return false
        }
        if (hit?.allowed == false) {
            adBlockLog { "showBlockDialog 拦截规则→return true(导航被拦,补记访问记录) url=$url" }
            // 导航在这里被拦掉，不会触发 onPageStarted / shouldInterceptRequest，
            // 手动补一条记录，保证「被拦截的请求也要显示在访问记录」。
            AdBlock.addUrl(url, true)
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
        val targetHost = url.toHttpUrlOrNull()?.host
        val differentSite = when {
            currentHost == null || targetHost == null -> true
            // 纯 IP 目标：topPrivateDomain() 为 null，退化为用 host 比较，避免 IP 站点每次点击都弹窗
            targetTop == null -> currentHost != targetHost
            else -> currentHost != targetTop
        }

        adBlockLog { "showBlockDialog 跨域判断 url=$url differentSite=$differentSite currentHost=$currentHost targetHost=$targetHost targetTop=$targetTop crossMode=${crossOriginMode()}" }
        if (!differentSite) {
            // 同主域，允许加载
            adBlockLog { "showBlockDialog 同主域→return false url=$url" }
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
            // 纯 IP 站点 topPrivateDomain() 为 null：规则与展示退化为 host（IP）本身
            val ruleHost = targetTop ?: url.toHttpUrlOrNull()?.host
            val originBinding = DialogCrossOriginBinding.inflate(LayoutInflater.from(context), null, false)
            originBinding.tvDialogUrl.text = ruleHost

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
                if (ruleHost != null) {
                    AdBlock.addWhiteHost(ruleHost, BLOCK_MODE_DOMAIN)
                }
                Log.i("跨域请求加入白名单：${ruleHost}")
                onAllow(url, request)
                dialog.dismiss()
            }
            originBinding.btnBlacklist.setOnClickListener {
                if (ruleHost != null) {
                    AdBlock.addBlackHost(ruleHost, BLOCK_MODE_DOMAIN)
                }
                dialog.dismiss()
                Log.i("跨域请求加入黑名单：${ruleHost}")
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
