package com.sjianjun.reader.tts.v2.load

import com.sjianjun.reader.tts.TtsChapterSplitter
import com.sjianjun.reader.tts.v2.session.LoadedChapter
import com.sjianjun.reader.tts.v2.session.NovelContentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 小说加载服务（会话层的 [NovelContentSource] 实现）。
 *
 * 只做「加载编排」，正文章节怎么取交给 [ChapterContentProvider]：
 * - **同一章并发只加载一次**：并发的 [loadChapter] 共用同一个任务，结果共享；
 * - **没有等待者延迟取消**：会话切章/退出后延迟 [CANCEL_GRACE_MS] 再取消，
 *   给"紧接着到来的新会话/预取"复用同一次加载的机会（与 [NovelChapterLoader] 一致）；
 * - **被外部取消时重试一次**：例如 `invalidate` 取消在途加载，等待方重新发起而不是直接失败；
 * - **内存缓存**：最近若干章命中直接返回（队列/预取/会话推进都会命中）；
 * - **失败重试**：[maxAttempts] 次，仍失败返回 null（会话层据此结束会话，不跳章）；
 * - **预取**：[prefetch] 在内部作用域提前加载，后续 [loadChapter] 直接命中缓存。
 *
 * 不负责：出声、通知、UI。
 */
class NovelLoadService(
    private val provider: ChapterContentProvider,
    private val scope: CoroutineScope,
    /** 内存缓存章节数 */
    private val maxCache: Int = 3,
    /** 单章最多尝试次数（含首次） */
    private val maxAttempts: Int = 3,
    /** 重试间隔 */
    private val retryDelayMs: Long = 300L,
) : NovelContentSource {

    private companion object {
        const val TAG = "NovelLoadService"

        /** 等待者归零后延迟取消：给紧接着到来的新请求/预取复用同一次加载的机会 */
        const val CANCEL_GRACE_MS = 500L
    }

    private class Entry(
        val deferred: Deferred<LoadedChapter?>,
    ) {
        /** 等待这章结果的调用方数量：减到 0 说明没人要了，可以取消加载 */
        val waiters = AtomicInteger(0)
    }

    /** 正在加载的章节：key -> 任务 */
    private val inflight = ConcurrentHashMap<String, Entry>()

    private val cacheLock = Any()

    /** 访问顺序 LRU：命中会移到末尾，超出容量淘汰最久未用的 */
    private val cache = object : LinkedHashMap<String, LoadedChapter>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LoadedChapter>?): Boolean =
            size > maxCache
    }

    // ==================== NovelContentSource ====================

    override suspend fun loadChapter(bookId: String, chapterIndex: Int): LoadedChapter? {
        if (bookId.isBlank() || chapterIndex < 0) return null
        val key = keyOf(bookId, chapterIndex)
        synchronized(cacheLock) { cache[key] }?.let { return it }

        var attempt = 0
        while (true) {
            attempt++
            val entry = inflight.computeIfAbsent(key) { Entry(startLoad(key, bookId, chapterIndex)) }
            entry.waiters.incrementAndGet()
            try {
                return entry.deferred.await()
            } catch (e: CancellationException) {
                // 调用方自己取消（会话停止/切章）：继续抛出
                currentCoroutineContext().ensureActive()
                inflight.remove(key, entry)
                if (attempt >= 2) {
                    Log.i("$TAG 加载被取消，按失败处理：$key")
                    return null
                }
                // 被外部取消（invalidate / 上一个等待方退出）：重开一次，别让本次请求跟着失败
                Log.i("$TAG 加载被取消，重新发起：$key")
            } finally {
                val remaining = entry.waiters.decrementAndGet()
                if (remaining <= 0) {
                    // 延迟取消：给紧接着到来的新请求/预取一个复用同一加载的机会
                    scope.launch {
                        delay(CANCEL_GRACE_MS)
                        if (entry.waiters.get() <= 0 && !entry.deferred.isCompleted) {
                            Log.i("$TAG 无等待者，取消加载：$key")
                            entry.deferred.cancel()
                        }
                        if (entry.deferred.isCompleted) inflight.remove(key, entry)
                    }
                } else if (entry.deferred.isCompleted) {
                    inflight.remove(key, entry)
                }
            }
        }
    }

    override suspend fun chapterCount(bookId: String): Int =
        runCatching { provider.chapterCount(bookId) }.getOrDefault(0)

    override fun prefetch(bookId: String, chapterIndex: Int) {
        if (bookId.isBlank() || chapterIndex < 0) return
        val key = keyOf(bookId, chapterIndex)
        if (synchronized(cacheLock) { cache[key] } != null) return
        if (inflight.containsKey(key)) return
        scope.launch {
            // 预取失败不影响朗读：加载失败就当没有预取到，真正读到时再重试
            runCatching { loadChapter(bookId, chapterIndex) }
                .onSuccess { if (it == null) Log.i("$TAG 预取失败：$key") }
        }
    }

    // ==================== 缓存/调试 ====================

    /** 作废某一章的缓存（正文更新/重新加载后调用） */
    fun invalidate(bookId: String, chapterIndex: Int) {
        val key = keyOf(bookId, chapterIndex)
        synchronized(cacheLock) { cache.remove(key) }
        inflight.remove(key)?.deferred?.cancel()
    }

    /** 作废整本书的缓存（目录整本重载后调用：章节链接可能变化） */
    fun invalidateBook(bookId: String) {
        if (bookId.isBlank()) return
        val prefix = "$bookId#"
        synchronized(cacheLock) {
            cache.keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
        }
        inflight.keys.filter { it.startsWith(prefix) }.forEach { key ->
            inflight.remove(key)?.deferred?.cancel()
        }
    }

    /** 当前缓存了哪些章节（测试/日志用） */
    fun cachedKeys(): List<String> = synchronized(cacheLock) { cache.keys.toList() }

    // ==================== 内部 ====================

    private fun keyOf(bookId: String, chapterIndex: Int) = "$bookId#$chapterIndex"

    private fun startLoad(key: String, bookId: String, chapterIndex: Int): Deferred<LoadedChapter?> =
        scope.async {
            var attempt = 0
            var result: LoadedChapter? = null
            while (attempt < maxAttempts) {
                val provided = try {
                    provider.load(bookId, chapterIndex)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e("$TAG 读取章节异常：$key", e)
                    null
                }
                if (provided != null) {
                    val paragraphs = splitParagraphs(provided)
                    if (paragraphs.isNotEmpty()) {
                        result = LoadedChapter(chapterIndex, provided.title, paragraphs)
                        synchronized(cacheLock) { cache[key] = result }
                        break
                    }
                }
                attempt++
                if (attempt < maxAttempts) {
                    Log.i("$TAG 加载失败，第${attempt}次重试：$key")
                    delay(retryDelayMs)
                }
            }
            if (result == null) {
                Log.e("$TAG 加载章节失败（已重试${maxAttempts - 1}次）：$key")
            }
            result
        }

    /** 分段：与阅读页/朗读共用的分段规则，空段自动丢弃 */
    private fun splitParagraphs(chapter: ProvidedChapter): List<String> =
        TtsChapterSplitter.split(chapter.title, chapter.text)
}
