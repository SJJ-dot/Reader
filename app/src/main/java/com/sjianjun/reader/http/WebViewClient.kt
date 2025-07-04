package com.sjianjun.reader.http

import android.os.Looper
import io.legado.app.help.http.BackstageWebView
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import sjj.alog.Log

//StringEscapeUtils.unescapeJson
object WebViewClient {
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    fun get(
        url: String? = null,
        headers: Map<String, String>? = null,
        javaScript: String = "document.documentElement.outerHTML",
        timeout: Long = 20000L,
    ): String {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("WebViewClient 必须在子线程中调用")
        }
        Log.i("web_get url: $url, headerMap: $headers, javaScript: $javaScript, timeout: $timeout")
        var result: String? = null
        GlobalScope.launch {
            try {
                result = BackstageWebView(
                    url = url,
                    headerMap = headers,
                    javaScript = javaScript,
                    timeout = timeout
                ).getResponse()
            } catch (e: Exception) {
                Log.e("WebViewClient get error", e)
                result = ""
            }
        }
        // 等待协程执行完成
        while (result == null) {
            Thread.sleep(100)
        }
        // 处理返回结果

        return result
    }
}