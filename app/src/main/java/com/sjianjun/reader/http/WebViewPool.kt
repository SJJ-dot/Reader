package com.sjianjun.reader.http

import android.annotation.SuppressLint
import android.os.Looper
import android.webkit.WebView
import com.sjianjun.reader.App
import com.sjianjun.reader.utils.init
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import sjj.alog.Log

/**
 * 长驻 WebView 池：复用 WebView 实例，避免每次请求都新建/销毁。
 *
 * - 通过 [Semaphore] 限制同时在用的 WebView 数量（默认 3）。
 * - [acquire] 优先复用空闲实例，不足时新建；[release] 仅归还（stopLoading），不销毁。
 * - 空闲超过 [IDLE_TIMEOUT_MS] 的实例会被定时清理任务销毁回收。
 * - 所有 WebView 的创建与状态操作都在主线程执行。
 */
object WebViewPool {
    private const val MAX_SIZE = 3
    private const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
    private const val IDLE_CHECK_INTERVAL_MS = 5 * 60 * 1000L

    private val semaphore = Semaphore(MAX_SIZE)
    private val idle = ArrayDeque<IdleEntry>()
    private var createdCount = 0

    /** 已创建的 WebView 总数（只增不减）：供测试断言"调用前拦截后没有新建 WebView" */
    val createdTotal: Int
        get() = createdCount

    private class IdleEntry(val webView: WebView, var lastUsed: Long)

    /** 从池中获取的 WebView 句柄，用完必须调用 [release] 或 [invalidate] 归还。 */
    class PooledWebView internal constructor(val webView: WebView) {
        /** 正常归还：WebView 会被复用。 */
        fun release() = WebViewPool.release(this)

        /** 异常归还：销毁该 WebView，下次 acquire 会新建。 */
        fun invalidate() = WebViewPool.invalidate(this)
    }

    init {
        GlobalScope.launch {
            while (true) {
                delay(IDLE_CHECK_INTERVAL_MS)
                withContext(Dispatchers.Main) { cleanupIdle() }
            }
        }
    }

    suspend fun acquire(): PooledWebView {
        semaphore.acquire()
        return withContext(Dispatchers.Main) {
            val webView = idle.removeLastOrNull()?.webView ?: createWebView()
            // 复用的 WebView 可能被上一个使用方（如验证页 onPause）调过 onPause 而处于暂停态，
            // 不恢复会导致加载后白屏/只渲染背景色（JS 与渲染被暂停）。统一恢复。
            webView.onResume()
            webView.clearHistory()
            PooledWebView(webView)
        }
    }

    fun release(pooled: PooledWebView) {
        doOnMain {
            val webView = pooled.webView
            try {
                webView.stopLoading()
                // 加载空白页，释放上一页的 DOM/资源，保持 WebView 干净待命
                webView.loadUrl("about:blank")
            } catch (e: Exception) {
                Log.e("WebViewPool release stopLoading error", e)
            }
            idle.addLast(IdleEntry(webView, System.currentTimeMillis()))
            semaphore.release()
        }
    }

    fun invalidate(pooled: PooledWebView) {
        doOnMain {
            try {
                pooled.webView.destroy()
            } catch (e: Exception) {
                Log.e("WebViewPool invalidate destroy error", e)
            }
            semaphore.release()
        }
    }

    private fun doOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            GlobalScope.launch(Dispatchers.Main) { block() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(App.app)
        webView.init(null)
        createdCount++
        Log.i("WebViewPool createWebView, totalCreated=$createdCount, idle=${idle.size}")
        return webView
    }

    private fun cleanupIdle() {
        val now = System.currentTimeMillis()
        val it = idle.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (now - entry.lastUsed > IDLE_TIMEOUT_MS) {
                it.remove()
                try {
                    entry.webView.destroy()
                } catch (e: Exception) {
                    Log.e("WebViewPool cleanupIdle destroy error", e)
                }
                Log.i("WebViewPool idle WebView destroyed by cleanup")
            }
        }
    }
}
