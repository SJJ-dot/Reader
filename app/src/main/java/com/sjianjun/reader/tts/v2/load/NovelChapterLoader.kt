package com.sjianjun.reader.tts.v2.load

import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.python.ScriptCallTargets
import com.sjianjun.reader.repository.BookUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import sjj.alog.Log

/**
 * 章节加载的底层能力（默认真实实现 [BookChapterBackend]）。
 *
 * 抽出来是为了单测/自测可以注入假实现（慢网络、失败重试、取消），不影响线上行为。
 */
interface ChapterLoadBackend {

    /** 加载一章第一页，结果写回 `chapter.content`；返回是否拿到可用内容 */
    suspend fun loadFirstPage(chapter: Chapter, force: Int): Boolean

    /** 续抓下一页，结果追加到 `chapter.content`；没有下一页或失败返回 false */
    suspend fun loadNextPage(chapter: Chapter): Boolean

    /** 取消该章还在跑的请求（跳章时让旧请求立刻让路） */
    fun cancel(chapter: Chapter)
}

/**
 * 真实实现：复用现有的 [BookUseCase]（数据库缓存优先 + 书源抓取 + 按章互斥/可取消）。
 */
class BookChapterBackend : ChapterLoadBackend {

    override suspend fun loadFirstPage(chapter: Chapter, force: Int): Boolean {
        BookUseCase.getChapterContent(chapter, force)
        return hasValidContent(chapter)
    }

    override suspend fun loadNextPage(chapter: Chapter): Boolean = BookUseCase.getChapterContentPage(chapter)

    override fun cancel(chapter: Chapter) {
        // 取消这一章（含分页 url）正在跑的脚本调用；它在锁内会立刻结束并让出锁
        runCatching { ScriptCallTargets.cancelCurrent(ScriptCallTargets.contentKey(chapter.url)) }
    }

    private fun hasValidContent(chapter: Chapter): Boolean {
        val content = chapter.content
        return !content.isNullOrEmpty() && content.firstOrNull()?.contentError == false
    }
}

/**
 * 阅读页章节加载入口：加载层的「整章分页」面。
 *
 * 与 [NovelLoadService]（朗读用的段落流）共用同一套底层抓取（[BookUseCase]）和同一份数据库缓存，
 * 但关心的事情不同：
 * - [NovelLoadService]：把一章变成"可朗读的段落"，供 TTS 会话推进/预取；
 * - [NovelChapterLoader]：把一章变成"阅读页可排版的分页内容"，供阅读页显示、预加载、批量缓存。
 *
 * 跳章体验：
 * - 阅读页的加载是"前台"（`foreground = true`）：切到新章时**取消上一章还在跑的请求**，
 *   新章立刻开始，不会排队等旧章（弱网/长章节时尤其明显）；
 * - 批量缓存、朗读预取是"后台"（`foreground = false`）：不抢占前台、也不会被前台误取消。
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

        /** 单章最多续抓的分页数（防止异常书源把分页循环卡死） */
        private const val MAX_PAGE_LOAD = 60

        /** 进程内单例（阅读页用） */
        val instance: NovelChapterLoader by lazy { NovelChapterLoader() }
    }

    /** 当前"前台"章节：阅读页正在加载/翻页的那一章 */
    @Volatile
    private var foregroundChapter: Chapter? = null

    /**
     * 加载一章的第一页（失败自动重试）。
     *
     * @param force -1 只用本地缓存，0 缓存优先，1 强制重新请求
     * @param retries 失败重试次数（含首次尝试共 retries 次）
     * @param foreground 是否前台请求（阅读页）；前台请求切章会取消上一章，后台（批量/预取）不会
     * @return 是否拿到可用内容
     */
    suspend fun loadChapter(
        chapter: Chapter,
        force: Int = 0,
        retries: Int = maxAttempts,
        foreground: Boolean = true,
    ): Boolean {
        if (foreground) switchForeground(chapter)
        val ok = retry(retries) { backend.loadFirstPage(chapter, force) && hasValidContent(chapter) }
        if (!ok) Log.e("$TAG 章节加载失败：${chapter.title} #${chapter.index}")
        return ok
    }

    /**
     * 续抓下一页（分页）。
     *
     * 没有下一页（章节读完）直接返回 false，不做重试；加载失败才重试。
     * 前台模式下，如果用户已经跳到别的章，旧章的翻页循环会立刻停止（返回 false）。
     */
    suspend fun loadNextPage(
        chapter: Chapter,
        retries: Int = maxAttempts,
        foreground: Boolean = true,
    ): Boolean {
        if (foreground) {
            val current = foregroundChapter
            if (current != null && keyOf(current) != keyOf(chapter)) {
                Log.i("$TAG 已切到其他章节，停止旧章翻页：${chapter.title}")
                return false
            }
            foregroundChapter = chapter
        }
        // 还没加载首页：先加载首页（调用方可能直接来要分页）
        if (chapter.content.isNullOrEmpty()) {
            return loadChapter(chapter, retries = retries, foreground = false)
        }
        if (chapter.content?.lastOrNull()?.nextPageUrl.isNullOrBlank()) return false
        return retry(retries) { backend.loadNextPage(chapter) }
    }

    /**
     * 加载整章：第一页 + 全部分页（批量缓存、朗读预取用）。
     *
     * @return 是否拿到可用内容
     */
    suspend fun loadWholeChapter(
        chapter: Chapter,
        force: Int = 0,
        retries: Int = maxAttempts,
        foreground: Boolean = false,
    ): Boolean {
        if (!loadChapter(chapter, force, retries, foreground)) return false
        var page = 0
        while (page < MAX_PAGE_LOAD && loadNextPage(chapter, retries, foreground)) {
            page++
        }
        return hasValidContent(chapter)
    }

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
     * 切换前台章节：新章 != 旧章时，取消旧章还在跑的请求（跳章要立刻响应）。
     */
    private fun switchForeground(chapter: Chapter) {
        val previous = foregroundChapter
        if (previous != null && keyOf(previous) != keyOf(chapter)) {
            Log.i("$TAG 章节跳转，取消上一章请求：${previous.title} #${previous.index}")
            runCatching { backend.cancel(previous) }
        }
        foregroundChapter = chapter
    }

    private fun keyOf(chapter: Chapter): String = "${chapter.bookId}|${chapter.url}"

    private fun hasValidContent(chapter: Chapter): Boolean {
        val content = chapter.content
        return !content.isNullOrEmpty() && content.firstOrNull()?.contentError == false
    }

    private suspend fun retry(retries: Int, block: suspend () -> Boolean): Boolean {
        val attempts = retries.coerceAtLeast(1)
        for (attempt in 1..attempts) {
            if (block()) return true
            if (attempt < attempts) delay(RETRY_DELAY_MS)
        }
        return false
    }
}
