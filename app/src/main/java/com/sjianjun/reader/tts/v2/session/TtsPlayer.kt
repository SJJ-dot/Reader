package com.sjianjun.reader.tts.v2.session

import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.tts.TtsChapterSplitter
import com.sjianjun.reader.tts.v2.TtsEngine
import com.sjianjun.reader.tts.v2.TtsEngineEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sjj.alog.Log

/**
 * 播放会话层：朗读的唯一"编排者"。
 *
 * 职责（都不属于引擎，也不属于 UI）：
 * - 会话状态机：开始/暂停/恢复/停止、小说与网页两种模式
 * - 章节推进：读完一章自动进入下一章；当前章加载失败则结束会话（不跳章，避免漏内容）
 * - 章级预取：开始一章时预取下一章（段级"合成领先播放"由引擎内部完成，会话层不参与）
 * - 进度换算：段落位置 + 段内进度 → 章节百分比与时间轴（媒体卡片/通知的时长与位置）
 * - 实时语速：调整后从当前段落重读一次，立即生效
 * - 定时停止（含"读完本章再停"）
 *
 * 不负责：出声（引擎层）、通知/媒体/焦点（通知层）、界面（交互层）、正文抓取（加载层）。
 *
 * 暂停/恢复以段落为准：暂停时记住当前段落，恢复从该段开头重读。
 */
class TtsPlayer(
    private val engine: TtsEngine,
    private val content: NovelContentSource,
    private val scope: CoroutineScope,
    /** 全书读完（最后一章朗读完成）时回调，供上层标记"已读完" */
    private val onBookEnd: ((bookId: String, bookTitle: String) -> Unit)? = null,
) {

    companion object {
        private const val TAG = "TtsPlayer"

        /** 语速变化后重读当前段的最小间隔（拖动滑块时避免频繁重读） */
        private const val RATE_RESTART_MIN_MS = 350L

        /** 每单位权重的基础耗时（语速 50 时约 200ms/字），仅用于展示时间轴 */
        private const val BASE_MS_PER_WEIGHT = 200f
    }

    private val _state = MutableStateFlow(TtsSessionState())
    val state: StateFlow<TtsSessionState> = _state.asStateFlow()

    private var playJob: Job? = null
    private var timerJob: Job? = null

    /** 网页模式的段落（暂停恢复时用） */
    private var pageParagraphs: List<String> = emptyList()

    /** 定时到点但设置了"读完本章再停" */
    private var pendingStopAfterChapter = false

    /**
     * 段落流里的一段（= 一章的段落）。
     *
     * 引擎只认识扁平下标，这里负责把扁平下标映射回"第几章 / 第几段"。
     */
    private class StreamSegment(
        /** 本段第一段在流内的扁平下标 */
        val flatStart: Int,
        val chapterIndex: Int,
        val title: String,
        val paragraphs: List<String>,
        /** 本段的起始段（只有它可能带段内偏移） */
        val startLocal: Int = 0,
        /** 起始段的段内偏移（字符数）：从本页第一行开始读时 >0 */
        val startOffset: Int = 0,
    )

    /** 当前段落流已提交给引擎的段落段（当前章 + 已追加的下一章） */
    private val segments = mutableListOf<StreamSegment>()

    /** 已经追加进引擎的最后一章（避免重复 append） */
    private var appendedChapter = -1

    /** 上次因语速变化重启当前段落的时间（拖动滑块时节流，避免频繁重读） */
    private var lastRateRestartAt = 0L

    /**
     * 本章各段的朗读累计权重与总权重：
     * 用来把「段落序号 + 段内进度」折算成时间轴（媒体卡片与通知的时长/位置）。
     */
    private var cumulativeWeights = LongArray(0)
    private var totalWeight = 0L

    // ==================== 会话控制 ====================

    /**
     * 小说模式：从指定章节的指定段落开始朗读。
     *
     * @param paragraphIndex 本页第一行所在的段落
     * @param startOffset 本页第一行在该段落文本里的字符偏移（跨页段落的前半部分不读）
     */
    fun startBook(
        bookId: String,
        bookTitle: String,
        chapterIndex: Int,
        paragraphIndex: Int = 0,
        startOffset: Int = 0,
    ) {
        if (bookId.isBlank() || chapterIndex < 0) {
            Log.e("$TAG 启动参数不合法 bookId=$bookId chapterIndex=$chapterIndex")
            return
        }
        cancelJob()
        pendingStopAfterChapter = false
        _state.value = TtsSessionState(
            mode = TtsSessionMode.BOOK,
            playing = true,
            bookId = bookId,
            bookTitle = bookTitle,
            chapterIndex = chapterIndex,
            paragraphIndex = paragraphIndex,
            paragraphStartOffset = startOffset,
        )
        launchChapter(chapterIndex, paragraphIndex, startOffset)
    }

    /** 网页模式：只读传入的正文，读完即停 */
    fun startPage(
        pageUrl: String,
        title: String,
        text: String,
        startIndex: Int = 0,
        startOffset: Int = 0,
    ) {
        cancelJob()
        pendingStopAfterChapter = false
        val split = TtsChapterSplitter.split(title, text)
        if (split.isEmpty()) {
            _state.value = TtsSessionState(error = "网页正文为空")
            return
        }
        val paragraphs = trimFirst(split, startIndex, startOffset)
        pageParagraphs = paragraphs
        val index = startIndex.coerceIn(0, paragraphs.lastIndex)
        _state.value = TtsSessionState(
            mode = TtsSessionMode.PAGE,
            playing = true,
            bookTitle = title,
            pageUrl = pageUrl,
            chapterIndex = 0,
            chapterTitle = title,
            paragraphIndex = index,
            paragraphCount = paragraphs.size,
            paragraphText = paragraphs[index],
            paragraphStartOffset = startOffset,
        )
        launchPage(index, startOffset)
    }

    /** 把起始段落按本页第一行的偏移裁掉前半部分（跨页段落只读本页开始的内容） */
    private fun trimFirst(paragraphs: List<String>, index: Int, startOffset: Int): List<String> {
        if (startOffset <= 0) return paragraphs
        val target = paragraphs.getOrNull(index) ?: return paragraphs
        if (target.length <= startOffset) return paragraphs
        return paragraphs.toMutableList().also { it[index] = target.substring(startOffset) }
    }

    /** 暂停：停引擎、记住当前段落（恢复时从该段开头重读） */
    fun pause() {
        val current = _state.value
        if (!current.active || !current.playing) return
        engine.stop()
        cancelJob()
        _state.value = current.copy(playing = false, paragraphProgress = 0f)
        Log.i("$TAG 暂停 第${current.paragraphIndex + 1}段")
    }

    fun resume() {
        val current = _state.value
        if (!current.active || current.playing) return
        _state.value = current.copy(playing = true, error = null)
        when (current.mode) {
            TtsSessionMode.BOOK -> launchChapter(current.chapterIndex, current.paragraphIndex, current.paragraphStartOffset)
            TtsSessionMode.PAGE -> launchPage(current.paragraphIndex, current.paragraphStartOffset)
            TtsSessionMode.NONE -> Unit
        }
    }

    fun stop() {
        val wasActive = _state.value.active
        cancelJob()
        engine.stop()
        timerJob?.cancel()
        timerJob = null
        pageParagraphs = emptyList()
        pendingStopAfterChapter = false
        cumulativeWeights = LongArray(0)
        totalWeight = 0L
        if (wasActive) {
            _state.value = TtsSessionState()
            Log.i("$TAG 停止")
        }
    }

    /**
     * 实时调整语速（0~100）。
     *
     * 引擎的语速只对"下一句/下一段"生效，所以正在朗读时从当前段落重读一次，立即听到变化；
     * 拖动滑块会连续调用，这里做 350ms 节流，避免一直重读同一段。
     */
    fun setRate(rate: Int) {
        engine.setRate(rate.coerceIn(0, 100))
        val current = _state.value
        if (!current.active || !current.playing) return
        val now = System.currentTimeMillis()
        if (now - lastRateRestartAt < RATE_RESTART_MIN_MS) return
        lastRateRestartAt = now
        Log.i("$TAG 语速变化，从第${current.paragraphIndex + 1}段重读以立即生效")
        when (current.mode) {
            TtsSessionMode.BOOK -> launchChapter(current.chapterIndex, current.paragraphIndex, current.paragraphStartOffset)
            TtsSessionMode.PAGE -> launchPage(current.paragraphIndex, current.paragraphStartOffset)
            TtsSessionMode.NONE -> Unit
        }
    }

    fun nextChapter() = skipChapter(1)

    fun prevChapter() = skipChapter(-1)

    private fun skipChapter(delta: Int) {
        val current = _state.value
        if (current.mode != TtsSessionMode.BOOK || current.chapterIndex < 0) return
        val target = current.chapterIndex + delta
        if (target < 0) return
        cancelJob()
        pendingStopAfterChapter = false
        _state.value = current.copy(
            playing = true,
            chapterIndex = target,
            paragraphIndex = 0,
            paragraphText = "",
            paragraphCount = 0,
            paragraphProgress = 0f,
            chapterProgressPercent = 0,
            chapterPositionMs = 0L,
            chapterDurationMs = 0L,
            error = null,
        )
        launchChapter(target, 0)
    }

    // ==================== 定时停止 ====================

    fun setSleepTimer(minutes: Int) {
        startTimer(if (minutes <= 0) 0L else minutes * 60_000L, minutes.coerceAtLeast(0))
    }

    /** 测试用：按毫秒设置定时 */
    internal fun setSleepTimerMillis(ms: Long) {
        startTimer(ms, ((ms + 59_999L) / 60_000L).toInt())
    }

    fun setStopAfterChapter(enabled: Boolean) {
        _state.update { it.copy(stopAfterChapter = enabled) }
    }

    private fun startTimer(ms: Long, minutes: Int) {
        timerJob?.cancel()
        timerJob = null
        if (ms <= 0L) {
            _state.update { it.copy(sleepTimerEndAt = 0L, sleepTimerMinutes = 0) }
            return
        }
        _state.update { it.copy(sleepTimerEndAt = System.currentTimeMillis() + ms, sleepTimerMinutes = minutes) }
        timerJob = scope.launch {
            delay(ms)
            if (!_state.value.active) return@launch
            if (_state.value.stopAfterChapter) {
                Log.i("$TAG 定时到点，等本章读完再停")
                pendingStopAfterChapter = true
                _state.update { it.copy(sleepTimerEndAt = 0L, sleepTimerMinutes = 0) }
            } else {
                Log.i("$TAG 定时到点，停止朗读")
                stop()
            }
        }
    }

    // ==================== 播放实现 ====================

    /**
     * 小说模式：以「段落流」方式朗读。
     *
     * 引擎只认识段落：先 [TtsEngine.speak] 当前章，当播放到"离本章末尾不足 [TtsEngine.lookahead] 段"时，
     * 把下一章的段落 [TtsEngine.append] 进去——顺序播放不重置，章与章之间不断流。
     * 跳章/定位才重开一条流（[TtsEngine.stop] + [speak]）。
     */
    private fun launchChapter(chapterIndex: Int, startParagraph: Int, startOffset: Int = 0) {
        cancelJob()
        playJob = scope.launch {
            if (_state.value.mode != TtsSessionMode.BOOK) return@launch
            _state.update {
                it.copy(
                    chapterIndex = chapterIndex,
                    chapterTitle = "",
                    paragraphCount = 0,
                    paragraphText = "",
                    paragraphProgress = 0f,
                    chapterProgressPercent = 0,
                    chapterPositionMs = 0L,
                    chapterDurationMs = 0L,
                )
            }
            val bookId = _state.value.bookId
            val loaded = content.loadChapter(bookId, chapterIndex)
            if (loaded == null || loaded.paragraphs.isEmpty()) {
                fail("章节内容加载失败")
                return@launch
            }
            val index = startParagraph.coerceIn(0, loaded.paragraphs.lastIndex)
            // 从本页第一行开始：起始段落裁掉本页之前的部分（上一页已显示过的内容不读）
            val paragraphs = trimFirst(loaded.paragraphs, index, startOffset)
            segments.clear()
            appendedChapter = -1
            segments.add(
                StreamSegment(
                    flatStart = 0,
                    chapterIndex = chapterIndex,
                    title = loaded.title,
                    paragraphs = paragraphs,
                    startLocal = index,
                    startOffset = startOffset,
                )
            )
            applySegmentState(segments.first(), index)
            // 章级预取：正文提前备好（段级合成领先由引擎内部处理）
            content.prefetch(bookId, chapterIndex + 1)
            Log.i(
                "$TAG 朗读第${chapterIndex + 1}章《${loaded.title}》共${loaded.paragraphs.size}段，从第${index + 1}段开始" +
                        if (startOffset > 0) "（跳过本段前${startOffset}字）" else ""
            )
            val completed = collectStream(engine.speak(paragraphs, index))
            if (!completed) return@launch
            // 流正常结束 = 没有下一章了（章末都是 append 续读，只有全书读完才 finish）
            if (!_state.value.playing) return@launch
            Log.i("$TAG 全书朗读完成")
            val session = _state.value
            onBookEnd?.invoke(session.bookId, session.bookTitle)
            stop()
        }
    }

    private fun launchPage(startParagraph: Int, startOffset: Int = 0) {
        cancelJob()
        playJob = scope.launch {
            val paragraphs = pageParagraphs
            if (paragraphs.isEmpty()) return@launch
            val index = startParagraph.coerceIn(0, paragraphs.lastIndex)
            segments.clear()
            appendedChapter = -1
            segments.add(
                StreamSegment(
                    flatStart = 0,
                    chapterIndex = 0,
                    title = _state.value.chapterTitle,
                    paragraphs = paragraphs,
                    startLocal = index,
                    startOffset = startOffset,
                )
            )
            applySegmentState(segments.first(), index)
            // 网页模式读完即停：开流时就告诉引擎"后面没有更多段落"
            val completed = collectStream(engine.speak(paragraphs, index, endOfStream = true))
            if (completed) {
                Log.i("$TAG 网页正文朗读完成")
                stop()
            }
        }
    }

    /** 收集引擎的段落流事件，直到读完/停止/失败；返回是否正常读完 */
    private suspend fun collectStream(events: Flow<TtsEngineEvent>): Boolean {
        var completed = false
        events.collect { event ->
            when (event) {
                is TtsEngineEvent.ParagraphStarted -> onParagraphStarted(event.index)

                is TtsEngineEvent.SegmentProgress -> onSegmentProgress(event.index, event.ratio)

                is TtsEngineEvent.ParagraphFinished -> onParagraphFinished(event.index)

                TtsEngineEvent.Completed -> completed = true

                is TtsEngineEvent.Failed -> fail(event.error.message ?: "朗读失败")
            }
        }
        return completed
    }

    /** 引擎开始读某段（扁平下标）：切到对应章节状态，并按需把下一章追加进引擎 */
    private suspend fun onParagraphStarted(flatIndex: Int) {
        val segment = segmentOf(flatIndex) ?: return
        val local = (flatIndex - segment.flatStart).coerceIn(0, segment.paragraphs.lastIndex)
        prepareWeights(segment.paragraphs)
        _state.update {
            it.copy(
                chapterIndex = segment.chapterIndex,
                chapterTitle = segment.title,
                paragraphCount = segment.paragraphs.size,
                paragraphIndex = local,
                paragraphText = segment.paragraphs.getOrNull(local) ?: "",
                // 起点偏移只对起始段有效，后面的段落都是完整朗读
                paragraphStartOffset = if (local == segment.startLocal) segment.startOffset else 0,
                paragraphProgress = 0f,
                playing = true,
                chapterProgressPercent = percent(local, 0f, segment.paragraphs.size),
                chapterPositionMs = positionAt(local, 0f),
                chapterDurationMs = durationMs(),
            )
        }
        // 只有小说模式才"续下一章"；网页模式读完即停，不能去加载"下一章"
        if (_state.value.mode == TtsSessionMode.BOOK) {
            maybeAppendNextChapter(segment, local)
        }
    }

    private fun onSegmentProgress(flatIndex: Int, ratio: Float) {
        val segment = segmentOf(flatIndex) ?: return
        val local = flatIndex - segment.flatStart
        _state.update {
            if (it.paragraphIndex != local || it.chapterIndex != segment.chapterIndex) {
                it
            } else {
                it.copy(
                    paragraphProgress = ratio,
                    chapterProgressPercent = percent(local, ratio, segment.paragraphs.size),
                    chapterPositionMs = positionAt(local, ratio),
                    chapterDurationMs = durationMs(),
                )
            }
        }
    }

    private fun onParagraphFinished(flatIndex: Int) {
        if (!pendingStopAfterChapter) return
        val segment = segmentOf(flatIndex) ?: return
        if (flatIndex - segment.flatStart >= segment.paragraphs.lastIndex) {
            pendingStopAfterChapter = false
            Log.i("$TAG 本章读完，按定时要求停止")
            stop()
        }
    }

    /**
     * 离本章末尾不足引擎领先窗口时，把下一章的段落追加进引擎（顺序播放，不重置）。
     *
     * 没有下一章了：调用 [TtsEngine.finish]，队列排空后引擎发 Completed。
     */
    private suspend fun maybeAppendNextChapter(segment: StreamSegment, localIndex: Int) {
        val remaining = segment.paragraphs.lastIndex - localIndex
        if (remaining > engine.lookahead) return
        val next = segment.chapterIndex + 1
        if (next <= appendedChapter) return
        val bookId = _state.value.bookId
        val total = content.chapterCount(bookId)
        if (total > 0 && next >= total) {
            appendedChapter = next
            Log.i("$TAG 已是最后一章，通知引擎收尾")
            engine.finish()
            return
        }
        val loaded = content.loadChapter(bookId, next)
        if (loaded == null || loaded.paragraphs.isEmpty()) {
            fail("章节内容加载失败")
            return
        }
        appendedChapter = next
        val flatStart = segments.last().let { it.flatStart + it.paragraphs.size }
        segments.add(StreamSegment(flatStart, next, loaded.title, loaded.paragraphs))
        engine.append(loaded.paragraphs)
        content.prefetch(bookId, next + 1)
        Log.i("$TAG 预追加第${next + 1}章《${loaded.title}》共${loaded.paragraphs.size}段到引擎队列")
    }

    /** 找到扁平下标所属的段落段（当前章或已追加的下一章） */
    private fun segmentOf(flatIndex: Int): StreamSegment? =
        segments.lastOrNull { it.flatStart <= flatIndex }

    /** 应用某一段（章）的状态：标题、段数、当前段、进度、时间轴 */
    private fun applySegmentState(segment: StreamSegment, localIndex: Int) {
        prepareWeights(segment.paragraphs)
        _state.update {
            it.copy(
                chapterIndex = segment.chapterIndex,
                chapterTitle = segment.title,
                paragraphCount = segment.paragraphs.size,
                paragraphIndex = localIndex,
                paragraphText = segment.paragraphs.getOrNull(localIndex) ?: "",
                paragraphStartOffset = if (localIndex == segment.startLocal) segment.startOffset else 0,
                paragraphProgress = 0f,
                chapterProgressPercent = percent(localIndex, 0f, segment.paragraphs.size),
                chapterPositionMs = positionAt(localIndex, 0f),
                chapterDurationMs = durationMs(),
            )
        }
    }

    private fun percent(index: Int, ratio: Float, count: Int): Int {
        if (count <= 0) return 0
        val value = (index + ratio.coerceIn(0f, 1f)) / count
        return (value * 100).toInt().coerceIn(0, 100)
    }

    /** 预计算各段累计权重，供时间轴估算（媒体卡片/通知的时长与位置） */
    private fun prepareWeights(paragraphs: List<String>) {
        val cumulative = LongArray(paragraphs.size + 1)
        for (index in paragraphs.indices) {
            cumulative[index + 1] = cumulative[index] + TtsChapterSplitter.speakingWeight(paragraphs[index])
        }
        cumulativeWeights = cumulative
        totalWeight = cumulative.lastOrNull()?.coerceAtLeast(1L) ?: 1L
    }

    /** 第 [index] 段、段内比例 [ratio] 处对应的章节时间位置（毫秒） */
    private fun positionAt(index: Int, ratio: Float): Long {
        if (cumulativeWeights.size < 2) return 0L
        val safeIndex = index.coerceIn(0, cumulativeWeights.lastIndex - 1)
        val before = cumulativeWeights[safeIndex]
        val current = cumulativeWeights[safeIndex + 1] - before
        val position = before + (current * ratio.coerceIn(0f, 1f)).toLong()
        return position * msPerWeight()
    }

    /** 本章预估总时长（毫秒） */
    private fun durationMs(): Long = totalWeight * msPerWeight()

    /**
     * 每单位权重的毫秒数（只用于展示时间轴：播放卡片/通知的时长与位置）。
     *
     * 优先用引擎实测值：各设备/各引擎的语速换算差异很大（实测本机语速 100 只比 50 快 1.27 倍，
     * 按公式会假设快 4 倍 → 时间轴严重偏差）。引擎读完几段后会给出校准值，公式只做兜底。
     */
    private fun msPerWeight(): Long {
        val measured = engine.calibratedMsPerWeight
        if (measured > 0L) return measured
        var rate = globalConfig.readerTtsRate.value ?: 50
        // 与引擎的语速换算保持一致：50 以上做放大
        if (rate > 50) rate = 50 + (rate - 50) * 3
        val speed = (rate / 50f).coerceIn(0.2f, 6f)
        return (BASE_MS_PER_WEIGHT / speed).toLong().coerceAtLeast(40L)
    }

    private fun fail(message: String) {
        Log.e("$TAG $message")
        playJob?.cancel()
        playJob = null
        engine.stop()
        _state.update { it.copy(playing = false, mode = TtsSessionMode.NONE, error = message) }
    }

    private fun cancelJob() {
        val job = playJob
        playJob = null
        job?.cancel()
    }
}
