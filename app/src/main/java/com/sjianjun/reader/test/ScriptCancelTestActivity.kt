package com.sjianjun.reader.test

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.databinding.ActivityScriptCancelTestBinding
import com.sjianjun.reader.http.WebViewPool
import com.sjianjun.reader.python.ScriptTaskCancelledException
import com.sjianjun.reader.python.ScriptTaskRegistry
import com.sjianjun.reader.python.ScriptTaskRunner
import com.sjianjun.reader.python.execModule
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.view.click
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
 * - 脚本中断：requests 调用中取消 / 调用前拦截；web_get 调用中取消 / 调用前拦截（Python 包装层与 Java 入口各一例）；
 *   verification_activity_get 调用中取消 / 调用前拦截；socket 阻塞取消；同一个 taskKey 复用
 * - 书籍加载：并发两次加载（旧的阻塞调用被取消、旧的迟到结果被丢弃）、协程取消（脚本终止 + 状态恢复）、
 *   重复加载同一章正文、同一书源重复搜索、正常加载对照
 *
 * 书籍加载用例会在库里临时建一个测试书源与测试书（无阅读记录，不会出现在书架），跑完自动清理。
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
    private var swallowUrl = ""

    private val groups: List<Group> = listOf(
        Group(
            "脚本中断", listOf(
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
        ),
        Group(
            "脚本命名空间缓存", listOf(
                Case("取消后命名空间保留（Session 可复用）") { caseNamespaceRetained(it) },
                Case("空闲超过30分钟回收、常用保留") { caseNamespaceIdleCleanup(it) },
            )
        ),
        Group(
            "书籍加载取消/并发", listOf(
                Case("并发两次：旧加载被取消") { caseBookConcurrentCancel() },
                Case("并发两次：旧加载迟到结果被丢弃") { caseBookStaleResultDiscarded() },
                Case("协程取消：脚本终止+状态恢复") { caseBookCoroutineCancel() },
                Case("重复加载同一章正文：前一次被取消") { caseChapterContentSuperseded() },
                Case("同一书源重复搜索：前一次被取消") { caseSearchSuperseded() },
                Case("正常加载：对照") { caseBookNormal() },
            )
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // 直接（adb）启动本页时，App.initialize() 可能因"未同意隐私政策"而没启动 Python，这里兜底
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
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
        swallowUrl = server.swallowUrl

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
        showLine("开始：本地黑洞服务 127.0.0.1:${server.port}\n")
        executor.execute {
            var passCount = 0
            var caseCount = 0
            groups.forEach { group ->
                showLine("—— ${group.title} ——")
                group.cases.forEach { case ->
                    val result = runCatching { case.block(case) }
                        .getOrElse { CaseResult(case.name, false, "用例异常：$it") }
                    passCount += if (result.pass) 1 else 0
                    caseCount++
                    Log.i("脚本中断测试 ${if (result.pass) "PASS" else "FAIL"} | ${result.name} | ${result.detail}")
                    showLine(format(result))
                }
            }
            runCatching { cleanupBookFixture() }
            val summary = "完成：$passCount/$caseCount 通过"
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
        // 每个用例用自己的挂起地址，避免相互干扰（本地服务对同一路径只在第一次请求时挂起）
        val outcome = runWithKey(newKey(), target, server.hangUrlFor(target), CANCEL_DELAY_MS)
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
        val outcome = runWithKey(newKey(), target, server.hangUrlFor(target), PRE_CANCEL_DELAY_MS)
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
        val first = runWithKey(key, "requests_block", server.hangUrlFor("reuse"), CANCEL_DELAY_MS)
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

    // ==================== 脚本命名空间缓存 ====================

    /**
     * 取消任务不应丢弃脚本命名空间：取消前后脚本里的模块级对象（Session）必须是同一个，
     * 否则下一次请求要重新 exec 脚本、重建连接（多一次 DNS/TLS）。
     */
    private fun caseNamespaceRetained(case: Case): CaseResult {
        val before = namespaceIds()
        val outcome = runWithKey(
            newKey(), "block", server.hangUrlFor("namespace"), CANCEL_DELAY_MS,
            script = NAMESPACE_TEST_SCRIPT
        )
        val after = namespaceIds()
        val finished = !outcome.alive
        val kept = before.isNotBlank() && before == after
        return CaseResult(
            case.name, finished && kept,
            buildString {
                append("取消").append(if (finished) "已生效" else "未生效")
                append("；命名空间标识 取消前=").append(before.take(20))
                append(" 取消后=").append(after.take(20))
                append("；保持一致=").append(kept)
            }
        )
    }

    /** 调用时按空闲时间回收：超过 30 分钟未使用的丢弃，刚刚用过的保留 */
    private fun caseNamespaceIdleCleanup(case: Case): CaseResult {
        val result = runCatching {
            execModule.callAttr("exec_script", NAMESPACE_TEST_SCRIPT, "test_idle_cleanup").toString()
        }.getOrElse { "异常：$it" }
        val pass = result.contains("stale_gone=True") && result.contains("fresh_kept=True")
        return CaseResult(case.name, pass, "结果=$result")
    }

    private fun namespaceIds(): String =
        runCatching {
            execModule.callAttr("exec_script", NAMESPACE_TEST_SCRIPT, "get_ids").toString()
        }.getOrElse { "" }

    // ==================== 书籍加载：取消 / 并发 ====================

    /** 测试书（id 与本地服务端口绑定） */
    private val testBookId: String get() = "http://127.0.0.1:${server.port}/book"

    /**
     * 并发两次加载：后一次开始时会取消前一次还在跑的脚本任务。
     * 前一次阻塞在 web_get（600s 超时），期望在新加载开始后 [CANCEL_FINISH_LIMIT_MS] 内结束。
     */
    private fun caseBookConcurrentCancel(): CaseResult {
        prepareBookFixture()
        // 同一本书同一个 URL：后一次加载取代前一次（本地服务对同一路径第二次请求会正常返回）
        val bookOld = bookWith(hangUrl)
        val bookNew = bookWith(hangUrl)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var finishedAfterNewMs = -1L
        runBlocking {
            val jobOld = scope.launch { runCatching { BookUseCase.reloadBookFromNet(bookOld) } }
            delay(FIRST_LOAD_DELAY_MS)
            val beginNewAt = System.currentTimeMillis()
            val jobNew = scope.launch { BookUseCase.reloadBookFromNet(bookNew) }
            jobNew.join()
            finishedAfterNewMs =
                if (withTimeoutOrNull(CANCEL_FINISH_LIMIT_MS) { jobOld.join(); true } == true) {
                    System.currentTimeMillis() - beginNewAt
                } else {
                    -1L
                }
        }
        scope.cancel()
        val oldDiscarded = bookOld.chapterList == null
        return bookResult(
            "并发两次：旧加载被取消",
            finishedAfterNewMs in 0..CANCEL_FINISH_LIMIT_MS && oldDiscarded,
            "新加载开始后旧加载${if (finishedAfterNewMs >= 0) "${finishedAfterNewMs}ms 内结束" else "未结束（仍阻塞中）"}" +
                "；旧加载结果${if (oldDiscarded) "未采用" else "被采用了"}" +
                "；新加载结果${if (bookNew.chapterList != null) "已采用" else "未采用"}",
            expectChapterFrom = hangUrl
        )
    }

    /**
     * 并发两次加载：旧加载吞掉取消异常后迟到返回（模拟"取消没生效"），
     * 期望它的结果被丢弃，不覆盖新加载写入的章节。
     */
    private fun caseBookStaleResultDiscarded(): CaseResult {
        prepareBookFixture()
        val bookOld = bookWith(swallowUrl)
        val bookNew = bookWith(swallowUrl)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var oldAlive = true
        runBlocking {
            val jobOld = scope.launch { runCatching { BookUseCase.reloadBookFromNet(bookOld) } }
            delay(FIRST_LOAD_DELAY_MS)
            val jobNew = scope.launch { BookUseCase.reloadBookFromNet(bookNew) }
            jobNew.join()
            oldAlive = withTimeoutOrNull(SWALLOW_RETURN_TIMEOUT_MS) { jobOld.join(); false } ?: true
        }
        scope.cancel()
        val oldDiscarded = bookOld.chapterList == null
        return bookResult(
            "并发两次：旧加载迟到结果被丢弃",
            !oldAlive && oldDiscarded,
            "旧加载${if (oldAlive) "超时未返回" else "已迟到返回"}，结果${if (oldDiscarded) "被丢弃" else "被采用了"}",
            expectChapterFrom = swallowUrl
        )
    }

    /**
     * 协程取消（页面销毁）：期望正在跑的脚本调用立刻终止，并且加载流程自己把"加载中"状态恢复掉。
     */
    private fun caseBookCoroutineCancel(): CaseResult {
        prepareBookFixture()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var finishedAfterCancelMs = -1L
        runBlocking {
            val job = scope.launch { runCatching { BookUseCase.reloadBookFromNet(bookWith(server.hangUrlFor("cancel"))) } }
            delay(FIRST_LOAD_DELAY_MS)
            val cancelAt = System.currentTimeMillis()
            job.cancel()
            finishedAfterCancelMs =
                if (withTimeoutOrNull(CANCEL_FINISH_LIMIT_MS) { job.join(); true } == true) {
                    System.currentTimeMillis() - cancelAt
                } else {
                    -1L
                }
            // 状态恢复是异步落库的，等它写完
            delay(300)
        }
        scope.cancel()
        return bookResult(
            "协程取消：脚本终止+状态恢复",
            finishedAfterCancelMs in 0..CANCEL_FINISH_LIMIT_MS,
            "协程取消后加载${if (finishedAfterCancelMs >= 0) "${finishedAfterCancelMs}ms 内结束" else "未结束（仍阻塞中）"}",
            expectChapterFrom = null
        )
    }

    /**
     * 重复加载同一章正文：新请求应立刻取消上一次请求（上一次正阻塞在脚本里、还占着章节锁），
     * 最终只有新请求的正文被写入。
     */
    private fun caseChapterContentSuperseded(): CaseResult {
        prepareBookFixture()
        val chapterUrl = server.hangChapterUrl
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var oldFinishedAfterNewMs = -1L
        var newError: Throwable? = null
        runBlocking {
            val jobOld = scope.launch {
                runCatching { BookUseCase.getChapterContent(chapterOf(chapterUrl), force = 1) }
            }
            delay(FIRST_LOAD_DELAY_MS)
            val beginNewAt = System.currentTimeMillis()
            val jobNew = scope.launch {
                runCatching { BookUseCase.getChapterContent(chapterOf(chapterUrl), force = 1) }
                    .onFailure { newError = it }
            }
            jobNew.join()
            oldFinishedAfterNewMs =
                if (withTimeoutOrNull(CANCEL_FINISH_LIMIT_MS) { jobOld.join(); true } == true) {
                    System.currentTimeMillis() - beginNewAt
                } else {
                    -1L
                }
        }
        scope.cancel()
        val content = runCatching {
            runBlocking {
                DbFactory.db.chapterContentDao().getChapterContent(testBookId, chapterUrl).first()
            }
        }.getOrElse { emptyList() }
        val text = content.firstOrNull()?.content ?: ""
        //正文内容 = 脚本返回的 "chapter-content:" + 页面内容（web_get 返回 outerHTML）
        val contentOk = content.size == 1 && text.startsWith("chapter-content:") && text.contains("ok")
        val pass = oldFinishedAfterNewMs in 0..CANCEL_FINISH_LIMIT_MS && newError == null && contentOk
        return CaseResult(
            "重复加载同一章正文：前一次被取消",
            pass,
            "新请求开始后旧请求${if (oldFinishedAfterNewMs >= 0) "${oldFinishedAfterNewMs}ms 内结束" else "未结束（仍阻塞中）"}" +
                "；正文=${content.size} 条 前缀=${text.take(18)}；新请求异常=${newError?.javaClass?.simpleName ?: "无"}"
        )
    }

    /**
     * 同一书源重复搜索（不区分关键词）：新搜索应立刻取消上一次搜索的脚本调用，新搜索正常返回结果。
     */
    private fun caseSearchSuperseded(): CaseResult {
        prepareBookFixture()
        val source = DbFactory.db.bookSourceDao().getBookSourceById(TEST_SOURCE_ID)!!
        // 旧搜索：关键词是会被挂住的地址；新搜索：普通关键词（不联网，立即返回）
        val oldQuery = server.hangSearchUrl
        val newQuery = "reader-test-keyword"
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var oldFinishedAfterNewMs = -1L
        var oldResult: List<*>? = null
        var newResult: List<*>? = null
        var newError: Throwable? = null
        runBlocking {
            val jobOld = scope.launch {
                runCatching { source.search(oldQuery) }.onSuccess { oldResult = it }
            }
            delay(FIRST_LOAD_DELAY_MS)
            val beginNewAt = System.currentTimeMillis()
            val jobNew = scope.launch {
                runCatching { source.search(newQuery) }
                    .onSuccess { newResult = it }
                    .onFailure { newError = it }
            }
            jobNew.join()
            oldFinishedAfterNewMs =
                if (withTimeoutOrNull(CANCEL_FINISH_LIMIT_MS) { jobOld.join(); true } == true) {
                    System.currentTimeMillis() - beginNewAt
                } else {
                    -1L
                }
        }
        scope.cancel()
        val pass = oldFinishedAfterNewMs in 0..CANCEL_FINISH_LIMIT_MS &&
            oldResult == null && newError == null && (newResult?.isNotEmpty() == true)
        return CaseResult(
            "同一书源重复搜索：前一次被取消",
            pass,
            "新搜索开始后旧搜索${if (oldFinishedAfterNewMs >= 0) "${oldFinishedAfterNewMs}ms 内结束" else "未结束（仍阻塞中）"}" +
                "；旧搜索${if (oldResult == null) "无结果" else "有结果"}" +
                "；新搜索结果=${newResult?.size ?: 0} 条；新搜索异常=${newError?.javaClass?.simpleName ?: "无"}"
        )
    }

    /** 对照：单次正常加载应写入章节并收尾 */
    private fun caseBookNormal(): CaseResult {
        prepareBookFixture()
        runBlocking { BookUseCase.reloadBookFromNet(bookWith(okUrl)) }
        return bookResult("正常加载：对照", true, "单次加载完成", expectChapterFrom = okUrl)
    }

    /**
     * 统一断言：章节是否只来自期望的那次加载、加载状态是否收尾、错误是否为空。
     * @param expectChapterFrom null 表示被放弃的加载不应写入任何章节
     */
    private fun bookResult(
        name: String,
        precondition: Boolean,
        preconditionDetail: String,
        expectChapterFrom: String?,
    ): CaseResult {
        val id = testBookId
        val book = runCatching { DbFactory.db.bookDao().getBookById(id) }.getOrNull()
        val chapters = runCatching {
            runBlocking { DbFactory.db.chapterDao().getChapterListByBookId(id).first() }
        }.getOrElse { emptyList() }
        val titles = chapters.map { it.title ?: "" }
        val chaptersOk = if (expectChapterFrom == null) {
            chapters.isEmpty()
        } else {
            chapters.size == 1 && titles.first() == chapterTitleOf(expectChapterFrom)
        }
        val pass = precondition && chaptersOk && book?.isLoading == false && book?.error == null
        return CaseResult(
            name, pass,
            "$preconditionDetail；章节=$titles；isLoading=${book?.isLoading}；error=${book?.error?.take(40) ?: "空"}"
        )
    }

    private fun chapterOf(url: String) = Chapter().apply {
        bookId = testBookId
        this.url = url
        title = "chapter-test"
    }

    private fun bookWith(url: String) = Book().apply {
        id = testBookId
        this.url = url
        title = TEST_BOOK_TITLE
        author = "test"
        bookSourceId = TEST_SOURCE_ID
    }

    private fun chapterTitleOf(url: String) = "chapter-from:$url"

    /** 建立测试书源与测试书（幂等），并清掉上一轮遗留的章节 */
    private fun prepareBookFixture() {
        val dao = DbFactory.db
        dao.bookSourceDao().insertBookSource(
            listOf(
                BookSource().apply {
                    id = TEST_SOURCE_ID
                    name = "CONC-SRC"
                    lauanage = BookSource.Language.py
                    js = BOOK_TEST_SCRIPT
                }
            )
        )
        dao.bookDao().insertBook(
            listOf(
                Book().apply {
                    id = testBookId
                    url = hangUrl
                    title = TEST_BOOK_TITLE
                    author = "test"
                    bookSourceId = TEST_SOURCE_ID
                }
            )
        )
        dao.chapterDao().deleteChapterByBookId(testBookId)
        dao.chapterContentDao().deleteChapterContentByBookId(testBookId)
    }

    private fun cleanupBookFixture() {
        val dao = DbFactory.db
        runCatching { dao.chapterContentDao().deleteChapterContentByBookId(testBookId) }
        runCatching { dao.chapterDao().deleteChapterByBookId(testBookId) }
        runCatching { dao.bookDao().deleteBookById(testBookId) }
        runCatching {
            dao.bookSourceDao().getBookSourceById(TEST_SOURCE_ID)?.let { dao.bookSourceDao().deleteBookSource(listOf(it)) }
        }
    }

    // ==================== 用例执行 ====================

    private fun runWithKey(
        key: String,
        func: String,
        url: String,
        cancelDelayMs: Long,
        script: String = TEST_SCRIPT,
    ): Outcome {
        val error = AtomicReference<Throwable?>()
        val value = AtomicReference<String?>()
        val thread = Thread({
            try {
                value.set(
                    runBlocking {
                        ScriptTaskRunner.run(key) {
                            execModule.callAttr("exec_script", script, func, url).toString()
                        }
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
            // 取消"该目标上当前这次调用"
            ScriptTaskRegistry.cancelTarget(key)
            Log.i("脚本中断测试 发起取消 target=$key func=$func")
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

    private class Case(val name: String, val func: String = "", val block: (Case) -> CaseResult)

    private class Group(val title: String, val cases: List<Case>)

    private class CaseResult(val name: String, val pass: Boolean, val detail: String)

    private class Outcome(
        val alive: Boolean,
        val elapsedAfterCancelMs: Long,
        val totalMs: Long,
        val error: Throwable?,
        val result: String?,
    )

    /**
     * 本地测试服务：
     * - `/ok`：立即返回 "ok"；
     * - 其他地址：**同一路径的第一次请求挂住不回**（让客户端一直等响应，只能被取消或超时），
     *   第二次请求则正常返回——这样可以构造"同一目标重复请求，第二次能成功"的场景。
     *
     * 用本地地址而不是不可达地址，保证"阻塞"是确定性的、与网络环境无关。
     */
    private class TestServer : Closeable {
        private val server = ServerSocket(0)
        private val clients = CopyOnWriteArrayList<Socket>()
        private val hungPaths = java.util.Collections.synchronizedSet(mutableSetOf<String>())
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
                        val path = request.lineSequence().firstOrNull()?.split(" ")?.getOrNull(1) ?: ""
                        if (path.contains("/ok") || !hungPaths.add(path)) {
                            socket.getOutputStream().write(OK_RESPONSE)
                            socket.getOutputStream().flush()
                            socket.close()
                        }
                        // 第一次请求该路径：不回任何数据，客户端只能被取消或等到自己的超时
                    }
                }.apply { isDaemon = true }.start()
            }
        }

        val port: Int get() = server.localPort
        val okUrl: String get() = "http://127.0.0.1:$port/ok"
        val hangUrl: String get() = hangUrlFor("book")
        val swallowUrl: String get() = hangUrlFor("swallow")
        val hangChapterUrl: String get() = hangUrlFor("chapter")
        val hangSearchUrl: String get() = hangUrlFor("search")

        /** 每个用例用独立路径：本地服务对同一路径只在第一次请求时挂起 */
        fun hangUrlFor(tag: String) = "http://127.0.0.1:$port/hang-$tag"

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

        /**
         * 命名空间缓存用例的测试脚本：
         * - 模块级 NAMESPACE_ID / SESSION 用于判断取消后命名空间（含 Session）是否被保留；
         * - block 用模块级 Session 请求挂起地址，验证取消时连接被关闭、命名空间仍可复用；
         * - test_idle_cleanup 直接操作 exec_script 的缓存，验证 30 分钟空闲回收。
         */
        private const val NAMESPACE_TEST_SCRIPT = """
import time
import uuid

import requests

NAMESPACE_ID = uuid.uuid4().hex
SESSION = requests.Session()


def get_ids():
    return NAMESPACE_ID + "|" + str(id(SESSION))


def block(url):
    return SESSION.get(url, timeout=600).text


def test_idle_cleanup():
    import exec_script as engine
    engine._namespace_cache["__idle_test__"] = [{"n": 1}, time.time() - engine._NAMESPACE_IDLE_SECONDS - 1]
    engine._namespace_cache["__fresh_test__"] = [{"n": 2}, time.time()]
    removed = engine._cleanup_namespaces()
    stale_gone = "__idle_test__" not in engine._namespace_cache
    fresh_kept = "__fresh_test__" in engine._namespace_cache
    engine._namespace_cache.pop("__fresh_test__", None)
    return "removed=%s|stale_gone=%s|fresh_kept=%s" % (removed, stale_gone, fresh_kept)
"""

        /**
         * 书籍加载用例的测试书源脚本：
         * - `/hang`：web_get 一直等响应（600s 超时），用来验证"被取消"；
         * - `/swallow`：故意吞掉取消异常后迟到返回，用来验证"迟到结果被丢弃"；
         * - 章节正文/搜索：请求本地测试服务，第一次挂住（验证被新请求取代）、第二次正常返回；
         * - 其他：立即返回，章节标题/正文/搜索结果里带上请求地址，便于断言是哪次调用写的。
         */
        private const val BOOK_TEST_SCRIPT = """
import time

from WebViewClient import web_get

BOOK_TITLE = "CONC-TEST-BOOK"


def getDetails(url, lastChapterUrl):
    if "/swallow" in url:
        # 故意吞掉取消异常：模拟"取消没生效"，验证迟到结果会被丢弃
        try:
            web_get(url, timeout=600000)
        except BaseException:
            pass
        time.sleep(1.0)
    elif "/hang" in url:
        web_get(url, timeout=600000)
    return {
        "url": url,
        "title": BOOK_TITLE,
        "author": "test",
        "chapterList": [{"title": "chapter-from:" + url, "url": url + "/c1"}],
    }


def getChapterContent(url):
    text = web_get(url, timeout=600000)
    return "chapter-content:" + text


def search(query):
    if query.startswith("http"):
        web_get(query, timeout=600000)
    return [{"bookTitle": "SEARCH-" + query, "bookAuthor": "test", "bookUrl": query}]
"""

        private const val TEST_SOURCE_ID = "test:CONC-SRC"
        private const val TEST_BOOK_TITLE = "CONC-TEST-BOOK"

        /** 第一次加载先跑起来，等它真正进入脚本阻塞后再触发第二次 */
        private const val FIRST_LOAD_DELAY_MS = 2000L

        /** 吞掉取消的旧加载迟到返回的等待上限 */
        private const val SWALLOW_RETURN_TIMEOUT_MS = 8000L

        /** 发起取消的时机：阻塞型用例已经进入阻塞，拦截型用例还在脚本里 sleep */
        private const val CANCEL_DELAY_MS = 1200L
        private const val PRE_CANCEL_DELAY_MS = 800L

        /** 取消后允许的最大收尾时间 */
        private const val CANCEL_FINISH_LIMIT_MS = 3000L

        /** 单个用例的等待上限（超过即判定失败，线程若仍存活则说明取消没生效） */
        private const val FINISH_TIMEOUT_MS = 15_000L
    }
}
