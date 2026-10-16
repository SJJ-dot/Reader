package com.sjianjun.reader.test

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.databinding.ActivityScriptCancelTestBinding
import com.sjianjun.reader.http.WebViewPool
import com.sjianjun.reader.python.ScriptTaskCancelledException
import com.sjianjun.reader.python.ScriptTaskRegistry
import com.sjianjun.reader.python.ScriptTaskRunner
import com.sjianjun.reader.python.execModule
import com.sjianjun.reader.view.click
import sjj.alog.Log
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 脚本中断能力自测页：打开即自动跑，测试脚本内置，不依赖外部网络（本地黑洞服务）。
 *
 * 覆盖：
 * - requests 直接请求：调用中取消、调用前拦截
 * - web_get：调用中取消、调用前拦截（Python 包装层 / Java 入口各一例）
 * - verification_activity_get：调用中取消、调用前拦截
 * - socket 阻塞：取消时 shutdown 唤醒阻塞中的 recv
 * - 同一个 taskKey 复用：取消后重新注册应正常工作（回归：旧的取消标记不能污染新任务）
 *
 * 判定：发起取消后脚本线程需在 [CANCEL_FINISH_LIMIT_MS] 内结束；"调用前拦截"用例还要求
 * 抛的是取消异常，且没有新建 WebView（`/hang` 让请求一直等响应，除取消外只能等到 600s 超时）。
 */
class ScriptCancelTestActivity : BaseActivity() {

    private val binding by lazy { ActivityScriptCancelTestBinding.inflate(layoutInflater) }
    private val executor = Executors.newSingleThreadExecutor()
    private val output = StringBuilder()
    private val running = AtomicBoolean(false)
    private val server = TestServer()

    private var hangUrl = ""
    private var okUrl = ""

    private val cases: List<Case> = listOf(
        Case("requests 调用中取消", "requests_block") { caseCancelDuring(it) },
        Case("requests 调用前拦截", "requests_after_cancel") { casePreCancel(it) },
        Case("web_get 调用中取消", "web_get_block") { caseCancelDuring(it) },
        Case("web_get 调用前拦截（Python 层）", "web_get_after_cancel") { casePreCancel(it) },
        Case("web_get 调用前拦截（Java 入口）", "web_get_java_after_cancel") { casePreCancel(it) },
        Case("verification_activity_get 调用中取消", "verification_block") { caseCancelDuring(it) },
        Case("verification_activity_get 调用前拦截", "verification_after_cancel") { casePreCancel(it) },
        Case("socket 阻塞取消", "socket_block") { caseCancelDuring(it) },
        Case("同 taskKey 复用", "requests_block") { caseKeyReuse() },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        server.start()
        hangUrl = server.hangUrl
        okUrl = server.okUrl

        binding.runButton.click { runAll() }
        runAll()
    }

    override fun onDestroy() {
        runCatching { server.close() }
        executor.shutdownNow()
        super.onDestroy()
    }

    // ==================== 用例调度 ====================

    private fun runAll() {
        if (!running.compareAndSet(false, true)) return
        output.setLength(0)
        binding.runButton.isEnabled = false
        showLine("开始：本地黑洞服务 127.0.0.1:${server.port}，共 ${cases.size} 个用例\n")
        executor.execute {
            val results = mutableListOf<CaseResult>()
            cases.forEach { case ->
                val result = runCatching { case.block(case) }
                    .getOrElse { CaseResult(case.name, false, "用例异常：$it") }
                results.add(result)
                Log.i("脚本中断测试 ${if (result.pass) "PASS" else "FAIL"} | ${result.name} | ${result.detail}")
                showLine(format(result))
            }
            val passCount = results.count { it.pass }
            val summary = "完成：$passCount/${results.size} 通过"
            Log.i("脚本中断测试 $summary")
            showLine(summary)
            runOnUiThread { binding.runButton.isEnabled = true }
            running.set(false)
        }
    }

    /** 阻塞中被取消：期望发起取消后很快结束 */
    private fun caseCancelDuring(case: Case): CaseResult {
        val target = case.func
        val webViewsBefore = WebViewPool.createdTotal
        val outcome = runWithKey(newKey(), target, hangUrl, CANCEL_DELAY_MS)
        val webViewsAfter = WebViewPool.createdTotal
        val finished = !outcome.alive
        val fast = outcome.elapsedAfterCancelMs in 0..CANCEL_FINISH_LIMIT_MS
        val pass = finished && fast
        return CaseResult(
            case.name, pass,
            buildString {
                append("取消后 ")
                append(if (outcome.elapsedAfterCancelMs >= 0) "${outcome.elapsedAfterCancelMs}ms 结束" else "未取消")
                append("；异常=").append(describe(outcome.error))
                append("；WebView 新建数 ").append(webViewsBefore).append("→").append(webViewsAfter)
            }
        )
    }

    /** 取消后再调用：期望在"调用前"就被拦下（立刻抛取消异常，且不产生副作用） */
    private fun casePreCancel(case: Case): CaseResult {
        val target = case.func
        val webViewsBefore = WebViewPool.createdTotal
        val outcome = runWithKey(newKey(), target, hangUrl, PRE_CANCEL_DELAY_MS)
        val webViewsAfter = WebViewPool.createdTotal
        val finished = !outcome.alive
        val fast = outcome.elapsedAfterCancelMs in 0..CANCEL_FINISH_LIMIT_MS
        val cancelled = isCancellation(outcome.error)
        val noNewWebView = webViewsAfter == webViewsBefore
        val pass = finished && fast && cancelled && noNewWebView
        return CaseResult(
            case.name, pass,
            buildString {
                append("取消后 ")
                append(if (outcome.elapsedAfterCancelMs >= 0) "${outcome.elapsedAfterCancelMs}ms 内结束" else "未取消")
                append("；异常=").append(describe(outcome.error))
                append("；是取消异常=").append(cancelled)
                append("；WebView 新建数 ").append(webViewsBefore).append("→").append(webViewsAfter)
            }
        )
    }

    /** 同一个 taskKey：先被取消，再用同一个 key 请求（应正常返回，不被旧的取消标记污染） */
    private fun caseKeyReuse(): CaseResult {
        val key = newKey()
        val first = runWithKey(key, "requests_block", hangUrl, CANCEL_DELAY_MS)
        val second = runWithKey(key, "requests_ok", okUrl, 0)
        val pass = !first.alive && !second.alive && second.error == null && second.result?.trim() == "ok"
        return CaseResult(
            "同 taskKey 复用", pass,
            buildString {
                append("第一次(取消)：")
                append(if (first.elapsedAfterCancelMs >= 0) "取消后 ${first.elapsedAfterCancelMs}ms 结束" else "未取消")
                append("；第二次(同 key)：")
                if (second.error == null) append("返回=${second.result}") else append("异常=${describe(second.error)}")
            }
        )
    }

    // ==================== 用例执行 ====================

    private fun runWithKey(key: String, func: String, url: String, cancelDelayMs: Long): Outcome {
        val error = AtomicReference<Throwable?>()
        val value = AtomicReference<String?>()
        val thread = Thread({
            try {
                value.set(
                    ScriptTaskRunner.run(key, TEST_SCRIPT) {
                        execModule.callAttr("exec_script", TEST_SCRIPT, func, url).toString()
                    }
                )
            } catch (t: Throwable) {
                error.set(t)
            }
        }, "script-cancel-test-$func")
        thread.isDaemon = true
        val start = System.currentTimeMillis()
        thread.start()
        var cancelAt = 0L
        if (cancelDelayMs > 0) {
            Thread.sleep(cancelDelayMs)
            cancelAt = System.currentTimeMillis()
            ScriptTaskRegistry.cancel(key)
            Log.i("脚本中断测试 发起取消 key=$key func=$func")
        }
        thread.join(FINISH_TIMEOUT_MS)
        val end = System.currentTimeMillis()
        return Outcome(
            alive = thread.isAlive,
            elapsedAfterCancelMs = if (cancelAt > 0) end - cancelAt else -1L,
            totalMs = end - start,
            error = error.get(),
            result = value.get(),
        )
    }

    private fun newKey() = "script-cancel-test-" + System.nanoTime()

    private fun isCancellation(t: Throwable?): Boolean {
        var e = t
        while (e != null) {
            if (e is ScriptTaskCancelledException) return true
            val text = e.toString() + " | " + (e.message ?: "")
            if (text.contains("ScriptAborted") || text.contains("ScriptTaskCancelled") ||
                text.contains("脚本任务已取消")
            ) {
                return true
            }
            e = e.cause
        }
        return false
    }

    private fun describe(t: Throwable?): String {
        t ?: return "无（正常返回）"
        val text = (t.message ?: t.toString()).replace("\n", " ")
        return "${t.javaClass.simpleName}: ${text.take(120)}"
    }

    private fun format(result: CaseResult) = "${if (result.pass) "[PASS]" else "[FAIL]"} ${result.name} | ${result.detail}"

    private fun showLine(line: String) {
        runOnUiThread {
            output.append(line).append('\n')
            binding.result.text = output.toString()
            binding.resultScroll.post { binding.resultScroll.fullScroll(android.view.View.FOCUS_DOWN) }
        }
    }

    private class Case(val name: String, val func: String, val block: (Case) -> CaseResult)

    private class CaseResult(val name: String, val pass: Boolean, val detail: String)

    private class Outcome(
        val alive: Boolean,
        val elapsedAfterCancelMs: Long,
        val totalMs: Long,
        val error: Throwable?,
        val result: String?,
    )

    /**
     * 本地测试服务：`/hang` 收下请求后不回任何数据（让客户端一直等响应），`/ok` 正常返回 "ok"。
     * 用本地地址而不是不可达地址，保证"阻塞"是确定性的、与网络环境无关。
     */
    private class TestServer : Closeable {
        private val server = ServerSocket(0)
        private val clients = CopyOnWriteArrayList<Socket>()
        private val acceptThread = Thread {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (t: Throwable) {
                    break
                }
                clients.add(socket)
                Thread {
                    runCatching {
                        val request = readRequestHead(socket)
                        if (request.startsWith("GET /ok") || request.contains(" /ok")) {
                            socket.getOutputStream().write(OK_RESPONSE)
                            socket.getOutputStream().flush()
                            socket.close()
                        }
                        // /hang：请求头收下后不回任何数据，客户端只能等到自己的超时或被取消
                    }
                }.apply { isDaemon = true }.start()
            }
        }

        val port: Int get() = server.localPort
        val hangUrl: String get() = "http://127.0.0.1:$port/hang"
        val okUrl: String get() = "http://127.0.0.1:$port/ok"

        fun start() {
            acceptThread.isDaemon = true
            acceptThread.start()
            Log.i("脚本中断测试 本地测试服务端口：$port")
        }

        override fun close() {
            runCatching { server.close() }
            clients.forEach { runCatching { it.close() } }
            clients.clear()
        }

        private fun readRequestHead(socket: Socket): String {
            val input = socket.getInputStream()
            val head = StringBuilder()
            var b = input.read()
            while (b != -1) {
                head.append(b.toChar())
                if (head.endsWith("\r\n\r\n")) break
                b = input.read()
            }
            return head.toString()
        }

        companion object {
            private val OK_RESPONSE =
                "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                    .toByteArray(Charsets.UTF_8)
        }
    }

    companion object {
        private const val TEST_SCRIPT = """
import socket as _socket
import time
from urllib.parse import urlparse

# 调用前拦截用例：先睡一会，让测试在"调用之前"就把任务取消掉
PRE_CALL_SLEEP = 2.0


def requests_block(url):
    import requests
    return requests.get(url, timeout=600).text


def requests_after_cancel(url):
    time.sleep(PRE_CALL_SLEEP)
    import requests
    return requests.get(url, timeout=600).text


def requests_ok(url):
    import requests
    return requests.get(url, timeout=30).text


def web_get_block(url):
    from WebViewClient import web_get
    return web_get(url, timeout=600000)


def web_get_after_cancel(url):
    time.sleep(PRE_CALL_SLEEP)
    from WebViewClient import web_get
    return web_get(url, timeout=600000)


def web_get_java_after_cancel(url):
    # 绕过 Python 包装层直接调 Java，验证 Java 入口自身的调用前拦截
    time.sleep(PRE_CALL_SLEEP)
    from java.util import HashMap
    from com.sjianjun.reader.http import WebViewClient
    return WebViewClient.get(url, HashMap(), "document.documentElement.outerHTML", 600000)


def verification_block(url):
    from SessionManager import verification_activity_get
    return verification_activity_get(url)


def verification_after_cancel(url):
    time.sleep(PRE_CALL_SLEEP)
    from SessionManager import verification_activity_get
    return verification_activity_get(url)


def socket_block(url):
    parsed = urlparse(url)
    sock = _socket.socket()
    sock.connect((parsed.hostname, parsed.port))
    sock.sendall(b"GET /hang HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
    data = sock.recv(1024)
    return str(len(data))
"""

        /** 发起取消的时机：阻塞型用例已经进入阻塞，拦截型用例还在脚本里 sleep */
        private const val CANCEL_DELAY_MS = 1200L
        private const val PRE_CANCEL_DELAY_MS = 800L

        /** 取消后允许的最大收尾时间 */
        private const val CANCEL_FINISH_LIMIT_MS = 3000L

        /** 单个用例的等待上限（超过即判定失败，线程若仍存活则说明取消没生效） */
        private const val FINISH_TIMEOUT_MS = 15_000L
    }
}
