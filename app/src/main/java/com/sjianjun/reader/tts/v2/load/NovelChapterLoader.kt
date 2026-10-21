package com.sjianjun.reader.tts.v2.load

import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ChapterContent
import com.sjianjun.reader.repository.BookUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 章节加载的底层能力（默认真实实现 [BookChapterBackend]）。
 *
 * 抽出来是为了自测可以注入假实现（慢网络、失败重试、并发共享），不影响线上行为。
 */
interface ChapterLoadBackend {

    /**
     * 加载一章正文，返回该章已缓存的内容（**不写入 chapter**）。
     *
     * 返回 null 表示失败（由上层决定是否重试）。
     */
    suspend fun loadFirstPage(chapter: Chapter, force: Int): List<ChapterContent>?
}

/**
 * 真实实现：复用现有的 [BookUseCase]（数据库缓存优先 + 书源抓取 + 按章互斥/可取消）。
 */
class BookChapterBackend : ChapterLoadBackend {

    override suspend fun loadFirstPage(chapter: Chapter, force: Int): List<ChapterContent>? {
        BookUseCase.getChapterContent(chapter, force)
        val pages = chapter.content ?: return null
        if (pages.isEmpty() || pages.firstOrNull()?.contentError != false) return null
        return pages.sortedBy { it.pageIndex }
    }
}

/**
 * 阅读页章节加载入口：加载层的「整章正文」面。
 *
 * 与 [NovelLoadService]（朗读用的段落流）共用同一套底层抓取（[BookUseCase]）和同一份数据库缓存：
 * - [NovelLoadService]：把一章变成"可朗读的段落"，供 TTS 会话推进/预取；
 * - [NovelChapterLoader]：把一章变成"阅读页可排版的正文"，供阅读页显示、预加载、批量缓存。
 *
 * 一章只抓一次（不再有 nextPageUrl 分页续抓）。
 *
 * 并发与取消（重点）：
 * - **同一章只加载一次**：谁先开始谁真正跑，后来者（阅读页、批量缓存、朗读）直接等结果，
 *   不会取消别人的请求（例如"缓存第 100 章时跳到第 100 章"，会复用正在跑的那次）；
 * - **没人等才取消**：等待者都走了（用户跳章/退出）才取消这次加载，避免白跑。
 */
class NovelChapterLoader(
    private val backend: ChapterLoadBackend = BookChapterBackend(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {

    companion object {
        private const val TAG = "NovelChapterLoader"

        private const val DEFAULT_MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1_000L

        /** 进程内单例（阅读页用） */
        val instance: NovelChapterLoader by lazy { NovelChapterLoader() }
    }

    /** 一次共享的加载：多个调用方 await 同一个 job */
    private class Flight<T>(val job: Deferred<T?>, val waiters: AtomicInteger = AtomicInteger(0))

    /** 章节加载：key = first|bookId|url|force */
    private val firstFlights = ConcurrentHashMap<String, Flight<List<ChapterContent>>>()

    /** 当前"前台"章节：阅读页正在加载/翻页的那一章 */
    @Volatile
    private var foregroundChapter: Chapter? = null

    /**
     * 加载一章的第一页（失败自动重试）。
     *
     * @param force -1 只用本地缓存，0 缓存优先，1 强制重新请求
     * @param retries 失败重试次数（含首次尝试共 retries 次）
     * @param foreground 是否前台请求（阅读页）；后台（批量缓存/预取）不参与"跳章停止翻页"
     * @return 是否拿到可用内容
     */
    suspend fun loadChapter(
        chapter: Chapter,
        force: Int = 0,
        retries: Int = maxAttempts,
        foreground: Boolean = true,
    ): Boolean {
        if (foreground) switchForeground(chapter)
        val key = "first|${keyOf(chapter)}|$force"
        val pages = join(firstFlights, key) {
            // 首页失败重试在共享 job 里做：只跑一次，后来者直接复用结果
            retry(retries) { backend.loadFirstPage(chapter, force) }
        }
        if (pages.isNullOrEmpty()) {
            Log.e("$TAG 章节加载失败：${chapter.title} #${chapter.index}")
            return false
        }
        // 结果同步到调用方自己的 chapter 对象（共享 job 用的是第一个请求者的对象）
        chapter.content = pages.toMutableList()
        chapter.isLoaded = true
        return true
    }

    /**
     * 加载整章（批量缓存、朗读预取用）。
     *
     * 一章只有一份正文，等价于 [loadChapter]；保留这个方法名是为了调用方语义清晰。
     *
     * @return 是否拿到可用内容
     */
    suspend fun loadWholeChapter(
        chapter: Chapter,
        force: Int = 0,
        retries: Int = maxAttempts,
        foreground: Boolean = false,
    ): Boolean = loadChapter(chapter, force, retries, foreground)

    /** 后台预取一章（不阻塞调用方，失败只记日志） */
    fun prefetch(chapter: Chapter) {
        if (hasValidContent(chapter)) return
        scope.launch {
            runCatching { loadWholeChapter(chapter, foreground = false) }
                .onFailure { Log.e("$TAG 预取失败：${chapter.title}", it) }
        }
    }

    /**
     * 批量缓存：从 [fromIndex] 到章节列表末尾，逐章加载整章。
     *
     * 返回的 Job 可取消（对应用户再次点击"缓存章节"）；[onProgress] 回调 (已完成, 总数)，
     * 在 IO 线程回调，UI 侧自行切主线程。
     */
    fun cacheBatch(
        chapters: List<Chapter>,
        fromIndex: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Job {
        val start = fromIndex.coerceIn(0, chapters.size)
        val total = chapters.size - start
        return scope.launch {
            var done = 0
            onProgress(done, total)
            for (index in start until chapters.size) {
                ensureActive()
                loadWholeChapter(chapters[index], foreground = false)
                done++
                onProgress(done, total)
                // 章节之间留一点间隔，避免连续请求把书源打爆（与旧实现一致）
                delay(50)
            }
            Log.i("$TAG 批量缓存完成：$total 章")
        }
    }

    /** 这一章是否已经有可用内容（不发起请求） */
    fun isLoaded(chapter: Chapter): Boolean = hasValidContent(chapter)

    // ==================== 内部实现 ====================

    /**
     * 共享一次加载：
     * - 已有同样的加载在跑 → 直接等（**不取消**别人的请求）；
     * - 等待者都走了才取消，避免白跑。
     */
    private suspend fun <T> join(
        flights: ConcurrentHashMap<String, Flight<T>>,
        key: String,
        block: suspend () -> T?,
    ): T? {
        val flight = flights.computeIfAbsent(key) { Flight(scope.async { block() }) }
        flight.waiters.incrementAndGet()
        try {
            return flight.job.await()
        } catch (e: CancellationException) {
            // 调用方自己被取消（跳章/退出）：继续抛出，让协程正常结束
            currentCoroutineContext().ensureActive()
            // 否则是共享加载被外部取消（如切书源重抓）：按"本次失败"处理，别把等待者一起取消
            Log.i("$TAG 共享加载被取消，按失败处理：$key")
            return null
        } finally {
            val remaining = flight.waiters.decrementAndGet()
            if (remaining <= 0 && !flight.job.isCompleted) {
                Log.i("$TAG 没有等待者，取消加载：$key")
                flight.job.cancel()
            }
            if (flight.job.isCompleted) flights.remove(key, flight)
        }
    }

    /**
     * 切换前台章节（跳章）：只记录，不取消共享加载。
     *
     * 旧的前台请求如果只有阅读页在等，会随协程取消而停止（见 [join] 的等待者计数）；
     * 如果批量缓存也在等同一章，会继续跑完，不会被误杀。
     */
    private fun switchForeground(chapter: Chapter) {
        val previous = foregroundChapter
        if (previous != null && keyOf(previous) != keyOf(chapter)) {
            Log.i("$TAG 章节跳转：${previous.title} -> ${chapter.title}")
        }
        foregroundChapter = chapter
    }

    private fun keyOf(chapter: Chapter): String = "${chapter.bookId}|${chapter.url}"

    private fun hasValidContent(chapter: Chapter): Boolean {
        val content = chapter.content
        return !content.isNullOrEmpty() && content.firstOrNull()?.contentError == false
    }

    private suspend fun <T> retry(retries: Int, block: suspend () -> T?): T? {
        val attempts = retries.coerceAtLeast(1)
        for (attempt in 1..attempts) {
            block()?.let { return it }
            if (attempt < attempts) delay(RETRY_DELAY_MS)
        }
        return null
    }
}
