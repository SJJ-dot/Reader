package com.sjianjun.reader.tts.v2

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.sjianjun.reader.App
import com.sjianjun.reader.preferences.globalConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import sjj.alog.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * 系统 TTS 引擎：包一层设备自带语音引擎（`android.speech.tts.TextToSpeech`）。
 *
 * 队列/播放：一次只提交一段（`QUEUE_FLUSH` + 段结束回调驱动下一段），
 * 这样段落边界、开始/结束都有明确回调，不会出现两段重叠。
 *
 * 段内进度（引擎内部实现）：
 * - 时间模型兜底：按段落权重估算本段耗时，100ms 上报一次；
 * - 引擎能给出真实位置时（`onRangeStart`，按词回调）取两者较大值，保证既及时又不回退；
 * - 段读完后用真实耗时校准「每权重耗时」，语速不同自动适应。
 */
class SystemTtsEngine(
    private val context: Context = App.app,
) : TtsEngine {

    companion object {
        private const val TAG = "SystemTtsEngine"

        /** 段内进度上报间隔 */
        private const val PROGRESS_TICK_MS = 100L

        /** 等引擎开始播放一段的超时：引擎没反应要报错，不能一直挂着 */
        private const val START_TIMEOUT_MS = 15_000L

        /** 等一段读完的超时（防引擎回调丢失） */
        private const val FINISH_TIMEOUT_MS = 120_000L
    }

    override val type = TtsEngineType.SYSTEM

    /** 每个引擎一份时间模型，系统 TTS 的每字耗时与 Edge 不同，各自校准 */
    private val model = ParagraphTimeModel(baseMsPerWeight = 200L)

    private var tts: TextToSpeech? = null
    private val stopped = AtomicBoolean(false)

    /** 本次 speak 的事件出口；stop() 时用它关闭事件流 */
    @Volatile
    private var out: ProducerScope<TtsEngineEvent>? = null

    /** 每次 speak 的代际：旧回调据此丢弃 */
    @Volatile
    private var generation = 0

    @Volatile
    private var startGate: CompletableDeferred<Unit>? = null

    @Volatile
    private var finishGate: CompletableDeferred<Unit>? = null

    @Volatile
    private var currentIndex = -1

    @Volatile
    private var currentText = ""

    /** 引擎回传的真实段内进度（-1 表示本段还没收到过） */
    @Volatile
    private var realRatio = -1f

    @Volatile
    private var lastError: Throwable? = null

    /** 会话层指定的语速（-1 表示跟随配置） */
    @Volatile
    private var rateOverride = -1

    /** 系统 TTS 只能一段一段提交（边合成边播），所以领先窗口是 1 段 */
    override val lookahead = 1

    /** 实测的每权重耗时（本机语音引擎差异很大，用实测值给会话层算时间轴） */
    override val calibratedMsPerWeight: Long get() = model.currentMsPerWeight

    /**
     * 段落流队列：元素是（流内扁平下标, 文本）。
     *
     * 单位是段落，与章节无关：`speak` 开启流，`append` 只往队尾追加，
     * 顺序播放不会因"进入下一章"而重置。
     */
    private val paragraphQueue = java.util.ArrayDeque<Pair<Int, String>>()

    /** 队列里下一段的扁平下标（append 追加时继续递增） */
    private var nextFlatIndex = 0

    /** 是否还有更多段落（[finish] 之后为 false，队列排空即 Completed） */
    @Volatile
    private var streamOpen = true

    override fun speak(
        paragraphs: List<String>,
        startIndex: Int,
        endOfStream: Boolean,
    ): Flow<TtsEngineEvent> = callbackFlow {
        // 本次朗读的所有权标记：新的 speak/stop 会抢占所有权，
        // 旧会话的清理（协程取消时触发）因此不会去停掉新会话的播放
        val myGeneration = ++generation
        out = this
        // 段落流：paragraphs[i] 的扁平下标就是 i（空段占位不读），之后 append 的段落继续往后编号
        val emptyStream = synchronized(paragraphQueue) {
            paragraphQueue.clear()
            nextFlatIndex = 0
            // 网页模式：读完即停；小说模式：后面还会 append，不能在这里收尾
            streamOpen = !endOfStream
            for (i in paragraphs.indices) {
                if (i >= startIndex && paragraphs[i].isNotBlank()) {
                    paragraphQueue.add(nextFlatIndex to paragraphs[i])
                }
                nextFlatIndex++
            }
            paragraphQueue.isEmpty()
        }
        if (emptyStream) {
            trySend(TtsEngineEvent.Completed)
            close()
            cleanup(myGeneration)
            return@callbackFlow
        }
        val engine = ensureEngine()
        if (engine == null) {
            trySend(TtsEngineEvent.Failed(IllegalStateException("系统语音引擎初始化失败")))
            close()
            cleanup(myGeneration)
            return@callbackFlow
        }
        stopped.set(false)
        try {
            while (!shouldStop(myGeneration)) {
                // 从段落流取下一段：没有就等 append / finish（顺序播放不重置）
                val item = synchronized(paragraphQueue) { paragraphQueue.pollFirst() }
                if (item == null) {
                    if (!streamOpen) {
                        trySend(TtsEngineEvent.Completed)
                        break
                    }
                    delay(20)
                    continue
                }
                val index = item.first
                val text = item.second
                if (!speakParagraph(engine, text, index)) {
                    trySend(TtsEngineEvent.Failed(IllegalStateException("合成失败（系统 TTS）：引擎拒绝朗读第${index + 1}段")))
                    break
                }
                // 等引擎真正开始播放（onStart）：没开始就报错，避免"看起来读完了其实没出声"
                val startGate = startGate
                val started = if (startGate == null) {
                    false
                } else {
                    withTimeoutOrNull(START_TIMEOUT_MS) { startGate.await() } != null
                }
                if (!started) {
                    trySend(TtsEngineEvent.Failed(IllegalStateException("合成失败（系统 TTS）：第${index + 1}段未开始播放")))
                    break
                }
                trySend(TtsEngineEvent.ParagraphStarted(index))
                // 段内进度：模型 + 引擎真实位置（取大者），直到本段读完
                val finishGate = finishGate
                val finished = if (finishGate == null) {
                    false
                } else {
                    withTimeoutOrNull(FINISH_TIMEOUT_MS) {
                        while (!finishGate.isCompleted && !shouldStop(myGeneration)) {
                            trySend(
                                TtsEngineEvent.SegmentProgress(
                                    index,
                                    maxOf(model.ratio(), realRatio.coerceAtLeast(0f))
                                )
                            )
                            delay(PROGRESS_TICK_MS)
                        }
                        !shouldStop(myGeneration) && finishGate.isCompleted
                    } ?: false
                }
                if (!finished) break
                val error = lastError
                if (error != null) {
                    trySend(TtsEngineEvent.Failed(IllegalStateException("合成失败（系统 TTS）：${error.message}", error)))
                    break
                }
                model.finishAndCalibrate()
                trySend(TtsEngineEvent.ParagraphFinished(index))
            }
        } finally {
            close()
            cleanup(myGeneration)
        }
    }

    /**
     * 追加段落（下一章内容）：只往队尾追加，顺序播放不重置。
     *
     * 段落流内扁平下标从当前值继续累加（会话层据此把下标映射回"章 / 段"）。
     */
    override fun append(paragraphs: List<String>) {
        if (paragraphs.isEmpty()) return
        val total = synchronized(paragraphQueue) {
            for (text in paragraphs) {
                if (text.isNotBlank()) paragraphQueue.add(nextFlatIndex to text)
                nextFlatIndex++
            }
            nextFlatIndex
        }
        Log.i("$TAG 追加 ${paragraphs.size} 段（流内共 $total 段）")
    }

    /** 全书读完：不再追加，队列排空后发 Completed */
    override fun finish() {
        streamOpen = false
        Log.i("$TAG 段落流结束（不再追加）")
    }

    override fun stop() {
        stopped.set(true)
        generation++
        runCatching { out?.close() }
        out = null
        startGate?.complete(Unit)
        finishGate?.complete(Unit)
        startGate = null
        finishGate = null
        runCatching { tts?.stop() }
        model.reset()
        // 清空段落流：下一次 speak 重新开始（跳章/定位就是这条路径）
        synchronized(paragraphQueue) {
            paragraphQueue.clear()
            nextFlatIndex = 0
        }
        streamOpen = false
        Log.i("$TAG stop")
    }

    override fun setRate(rate: Int) {
        rateOverride = rate.coerceIn(0, 100)
        // 立即应用到引擎：后续 utterance 用新语速（当前这句不受影响，由会话层重读本段）
        tts?.let { applyRate(it) }
        Log.i("$TAG 语速调整为 $rateOverride")
    }

    override fun release() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }

    // ==================== 内部实现 ====================

    private fun shouldStop(myGeneration: Int): Boolean = stopped.get() || generation != myGeneration

    /** 提交一段朗读；返回 false 表示引擎直接拒绝 */
    private fun speakParagraph(engine: TextToSpeech, text: String, index: Int): Boolean {
        currentIndex = index
        currentText = text
        realRatio = -1f
        lastError = null
        model.start(text)
        startGate = CompletableDeferred()
        finishGate = CompletableDeferred()
        applyRate(engine)
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "p$index")
        }.getOrElse {
            Log.e("$TAG speak 异常，段=$index", it)
            TextToSpeech.ERROR
        }
        if (result == TextToSpeech.ERROR) {
            Log.e("$TAG speak 返回 ERROR，段=$index")
            return false
        }
        return true
    }

    private fun applyRate(engine: TextToSpeech) {
        var rate = if (rateOverride >= 0) rateOverride else (globalConfig.readerTtsRate.value ?: 50)
        // 与现有实现保持一致：50 以上做放大，让高语速区分更明显
        if (rate > 50) rate = 50 + (rate - 50) * 3
        runCatching { engine.setSpeechRate(max(rate / 50f, 0.01f)) }
    }

    private suspend fun ensureEngine(): TextToSpeech? {
        tts?.let { return it }
        val created = suspendCancellableCoroutine { cont ->
            val engine = TextToSpeech(context) { status ->
                Log.i("$TAG 初始化结果：$status")
                if (cont.isActive) cont.resume(status == TextToSpeech.SUCCESS)
            }
            tts = engine
        }
        val engine = tts
        if (!created || engine == null) {
            runCatching { engine?.shutdown() }
            tts = null
            return null
        }
        engine.setOnUtteranceProgressListener(listener)
        return engine
    }

    /**
     * 清理本次朗读的临时状态（幂等）。
     *
     * 只有仍然持有所有权（没有更新的朗读/停止接管）时才动引擎，
     * 否则会把紧接着启动的新朗读一起停掉（表现为切章后没有声音、进度不走）。
     */
    private fun cleanup(myGeneration: Int) {
        if (generation != myGeneration) {
            Log.i("$TAG 旧朗读已结束且被新朗读接管，跳过清理")
            return
        }
        stopped.set(true)
        generation++
        startGate?.complete(Unit)
        finishGate?.complete(Unit)
        startGate = null
        finishGate = null
        currentIndex = -1
        currentText = ""
        realRatio = -1f
        out = null
        model.reset()
        runCatching { tts?.stop() }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            startGate?.complete(Unit)
        }

        override fun onDone(utteranceId: String?) {
            finishGate?.complete(Unit)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            lastError = IllegalStateException("语音引擎朗读失败（$utteranceId）")
            startGate?.complete(Unit)
            finishGate?.complete(Unit)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            lastError = IllegalStateException("语音引擎朗读失败（code=$errorCode）")
            startGate?.complete(Unit)
            finishGate?.complete(Unit)
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val text = currentText
            if (text.isEmpty()) return
            val ratio = (start.toFloat() / text.length).coerceIn(0f, 1f)
            if (ratio > realRatio) realRatio = ratio
        }
    }
}
