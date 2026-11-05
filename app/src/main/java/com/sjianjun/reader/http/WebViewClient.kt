package com.sjianjun.reader.http

import android.os.Looper
import com.sjianjun.reader.python.ScriptTaskCancelledException
import com.sjianjun.reader.python.ScriptTaskRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.util.concurrent.atomic.AtomicReference

//StringEscapeUtils.unescapeJson
object WebViewClient {

    private const val WAIT_INTERVAL_MS = 50L

    @JvmStatic
    fun get(
        url: String? = null,
        headers: Map<String, String>? = null,
        javaScript: String = "document.documentElement.outerHTML",
        timeout: Long = 20000L,
    ): String {
        checkThread()
        // 调用前拦截：脚本任务已取消时不再新建 WebView，直接抛取消异常
        ScriptTaskRegistry.throwIfCurrentCancelled()
        Log.i("WebViewClient url: $url, headerMap: $headers, javaScript: $javaScript, timeout: $timeout")
        return awaitResponse("get") {
            BackstageWebView(
                url = url,
                headerMap = headers,
                javaScript = javaScript,
                timeout = timeout
            ).getResponse()
        }
    }

    @JvmStatic
    fun post(
        url: String,
        postData: String = "",
        javaScript: String = "document.documentElement.outerHTML",
        timeout: Long = 20000L,
        encoding: String = "UTF-8"
    ): String {
        checkThread()
        // 调用前拦截：脚本任务已取消时不再新建 WebView，直接抛取消异常
        ScriptTaskRegistry.throwIfCurrentCancelled()
        Log.i("WebViewClient post url: $url, timeout: $timeout")
        return awaitResponse("post") {
            BackstageWebView(
                url = url,
                javaScript = javaScript,
                timeout = timeout,
                postData = postData,
                encoding = encoding,
            ).getResponse()
        }
    }

    private fun checkThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("WebViewClient 必须在子线程中调用")
        }
    }

    /**
     * 阻塞等待 WebView 结果。
     *
     * 这里不能用 GlobalScope 自旋：脚本任务被取消（例如退出阅读页）时要立刻停止加载并归还 WebView，
     * 所以把等待用的 job 登记到脚本任务上，取消时 job.cancel()，BackstageWebView 的
     * invokeOnCancellation 会 release 掉 WebView，本方法随即抛出取消异常让脚本退出。
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun awaitResponse(tag: String, request: suspend () -> String): String {
        val result = AtomicReference<String?>(null)
        val failure = AtomicReference<Throwable?>(null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scope.launch {
            try {
                result.set(request())
            } catch (e: Throwable) {
                failure.set(e)
            }
        }
        val cancelAction: () -> Unit = { job.cancel() }
        val task = ScriptTaskRegistry.current()
        task?.addCancelAction(cancelAction)
        try {
            // 等待协程执行完成
            while (result.get() == null && failure.get() == null) {
                if (job.isCancelled) {
                    Log.i("WebViewClient $tag 已取消")
                    throw ScriptTaskCancelledException()
                }
                Thread.sleep(WAIT_INTERVAL_MS)
            }
        } finally {
            task?.removeCancelAction(cancelAction)
        }
        failure.get()?.let {
            Log.e("WebViewClient $tag error", it)
            return ""
        }
        // 处理返回结果
        return result.get() ?: ""
    }
}
