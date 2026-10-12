package com.sjianjun.reader.module.feedback

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.sjianjun.reader.utils.gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 网页 ↔ 原生 的桥。
 *
 * 页面里调用 window.FeedbackBridge.rpc(id, 函数名, 参数JSON)，
 * 原生算完再回调 window.__fbResult(id, 结果JSON)，与浏览器版的 rpc() 行为对齐：
 * 成功给云函数的原始返回值，失败给 {"error":"network_error"}（页面据此走统一的报错文案）。
 *
 * 只放行 fb_ 前缀的云函数，页面拿不到任意网络能力。
 */
class FeedbackBridge(
    private val webView: WebView,
    private val scope: CoroutineScope
) {

    @JavascriptInterface
    fun rpc(id: String, function: String, argsJson: String) {
        if (!function.startsWith("fb_")) {
            callback(id, """{"error":"forbidden_function"}""")
            return
        }
        scope.launch {
            val payload = FeedbackApi.call(function, argsJson)
            withContext(Dispatchers.Main) {
                // 调用失败给错误对象（页面会 reject 并提示网络异常）；
                // 成功则原样透传，含 fb_thread 找不到时返回的 null。
                callback(id, payload ?: """{"error":"network_error"}""")
            }
        }
    }

    /** 把结果作为 JS 字符串字面量回传（JSON 字符串本身就是合法的 JS 字符串） */
    private fun callback(id: String, payload: String) {
        val js = "window.__fbResult && window.__fbResult(${gson.toJson(id)}, ${gson.toJson(payload)})"
        webView.evaluateJavascript(js, null)
    }
}
