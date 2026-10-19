package com.sjianjun.reader.tts.v2

import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.tts.TtsChapterSplitter
import io.legado.app.service.TtsEdgeManager
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import sjj.alog.Log

/**
 * Edge TTS 引擎：包一层现有的 [TtsEdgeManager]（在线合成，内部自带
 * 「文本队列 → 合成任务 → 音频队列（流式 MediaDataSource）→ 播放任务」）。
 *
 * 段内进度由引擎内部处理：Edge 的合成音频是固定码率 mp3，
 * 直接按「播放位置 / 时长（或已合成字节数换算）」得到真实进度，比时间模型准，
 * 段与段之间（下一段还在合成时）进度自然停在下一段的 0%。
 *
 * 这里只做事件适配：把 manager 的回调转成 [TtsEngineEvent]，并补出「上一段读完」事件
 * （manager 只在下一段开始播放时回调段落号）。
 */
class EdgeTtsEngine(
    /** 语速比例，沿用现有配置口径（readerTtsRate，0~100） */
    rate: Int = globalConfig.readerTtsRate.value ?: 50,
    private val voice: String = globalConfig.readerTtsVoice.value ?: "zh-CN-XiaoxiaoNeural",
) : TtsEngine {

    /** 当前语速（会话层可实时调整；合成时按此值生效） */
    @Volatile
    private var rate: Int = rate.coerceIn(0, 100)

    companion object {
        private const val TAG = "EdgeTtsEngine"
    }

    override val type = TtsEngineType.EDGE

    private var manager: TtsEdgeManager? = null

    @Volatile
    private var out: ProducerScope<TtsEngineEvent>? = null

    /** 朗读所有权：每次 speak/stop 都会变化，用来让旧会话的清理失效 */
    @Volatile
    private var owner = 0

    /** 最近一个开始播放的段落（Edge 没有"读完"回调，靠下一段开始/全部完成来补） */
    private var lastStarted = -1

    /** 当前段落流已编号的段落数（append 追加的段落接着往后编号） */
    @Volatile
    private var flatCount = 0

    /** 本段开始播放的时间（用于实测每权重耗时） */
    @Volatile
    private var paragraphStartedAt = 0L

    /** 实测的每权重耗时（毫秒）；<=0 表示还没样本 */
    @Volatile
    private var measuredMsPerWeight = -1L

    /** Edge 内部会预合成多段（合成线程领先播放），领先窗口 5 段 */
    override val lookahead = 5

    /** 用真实播放时长校准出来的每权重耗时，供会话层算时间轴 */
    override val calibratedMsPerWeight: Long get() = measuredMsPerWeight

    override fun speak(
        paragraphs: List<String>,
        startIndex: Int,
        endOfStream: Boolean,
    ): Flow<TtsEngineEvent> = callbackFlow {
        // 本次朗读的所有权标记：新的 speak/stop 会抢占所有权，
        // 旧会话的清理（切章/停止时协程取消触发）因此不会去停掉新会话的播放
        val myOwner = ++owner
        out = this
        val start = startIndex.coerceAtLeast(0)
        if (start >= paragraphs.size) {
            trySend(TtsEngineEvent.Completed)
            close()
            awaitClose { cleanupIfOwner(myOwner) }
            return@callbackFlow
        }
        // 每次朗读用独立的 manager（独立合成任务与音频管道）：
        // Edge 服务端在章节之间会关闭 socket，复用连接省不了握手；
        // 而跨会话共享同一实例时新旧合成任务会互相踩踏（切章后没有声音、进度不走）
        runCatching { manager?.stop() }
        runCatching { manager?.release() }
        val m = TtsEdgeManager().also { manager = it }
        lastStarted = -1
        // 扁平下标 = 传入列表的下标（空段由 manager 跳过，但下标照常占用），append 的段落接着编号
        flatCount = paragraphs.size
        // 段落开始播放（manager 在播放器就绪、开始播放本段时回调，回传的就是扁平下标）
        m.paragraphChangeCallback = { index ->
            val previous = lastStarted
            val now = System.currentTimeMillis()
            if (previous >= 0 && previous != index) {
                recordParagraphDuration(previous, now, paragraphs)
                trySend(TtsEngineEvent.ParagraphFinished(previous))
            }
            lastStarted = index
            paragraphStartedAt = now
            trySend(TtsEngineEvent.ParagraphStarted(index))
        }
        // 段内进度：manager 按播放位置/时长轮询回传（100ms）
        m.segmentProgressCallback = { ratio ->
            val index = lastStarted
            if (index >= 0) {
                trySend(TtsEngineEvent.SegmentProgress(index, ratio.coerceIn(0f, 1f)))
            }
        }
        m.onParagraphsCompleted = {
            val last = lastStarted
            if (last >= 0) {
                recordParagraphDuration(last, System.currentTimeMillis(), paragraphs)
                trySend(TtsEngineEvent.ParagraphFinished(last))
                lastStarted = -1
            }
            trySend(TtsEngineEvent.Completed)
            close()
        }
        // 弱网：预加载失败会重试；播放追上来时仍失败 → 上报，会话层停止并提示用户
        m.onParagraphsError = { message ->
            trySend(TtsEngineEvent.Failed(IllegalStateException(message)))
            close()
        }
        val started = m.startParagraphs(paragraphs, start, rate = rate, voice = voice)
        if (!started) {
            trySend(TtsEngineEvent.Failed(IllegalStateException("Edge 引擎启动失败")))
            close()
        }
        // 网页模式：读完即停（不需要上层再 append）
        if (endOfStream) m.finishParagraphs()
        Log.i("$TAG start paragraphs=${paragraphs.size} from=$start rate=$rate voice=$voice started=$started owner=$owner")
        awaitClose { cleanupIfOwner(myOwner) }
    }

    /**
     * 追加段落（顺序播放不重置）：不重建 manager/连接，只把段落排到合成队列尾部。
     *
     * 扁平下标从 [flatCount] 继续编号，事件里回传的就是它。
     */
    override fun append(paragraphs: List<String>) {
        if (paragraphs.isEmpty()) return
        val m = manager ?: return
        val base = flatCount
        flatCount += paragraphs.size
        m.appendParagraphs(paragraphs, base, rate, voice)
        Log.i("$TAG append 段数=${paragraphs.size} base=$base")
    }

    /** 全书读完：让 manager 收尾（队列排空后回调 Completed） */
    override fun finish() {
        manager?.finishParagraphs()
    }

    /** 用一段的真实播放时长校准「每权重耗时」（平滑，避免单段抖动） */
    private fun recordParagraphDuration(index: Int, endAt: Long, paragraphs: List<String>) {
        val startAt = paragraphStartedAt
        if (startAt <= 0L) return
        val weight = TtsChapterSplitter.speakingWeight(paragraphs.getOrNull(index))
        if (weight <= 0L) return
        val sample = ((endAt - startAt) / weight).coerceIn(40L, 800L)
        measuredMsPerWeight =
            if (measuredMsPerWeight <= 0L) sample else (measuredMsPerWeight * 2 + sample) / 3
    }

    override fun stop() {
        // 抢占所有权：正在结束的旧朗读不能再停掉接下来的新朗读
        owner++
        stopInternal()
        Log.i("$TAG stop")
    }

    /**
     * 旧朗读结束时的清理：只有仍持有所有权才停引擎。
     *
     * 否则切章/切段时，旧 Flow 的清理会落在共享的 [TtsEdgeManager] 上，
     * 把刚刚启动的新朗读一起停掉（表现为没有声音、进度不走）。
     */
    private fun cleanupIfOwner(myOwner: Int) {
        if (owner != myOwner) {
            Log.i("$TAG 旧朗读已结束且被新朗读接管，跳过清理 myOwner=$myOwner owner=$owner")
            return
        }
        Log.i("$TAG 朗读结束，清理自己 myOwner=$myOwner")
        owner++
        stopInternal()
    }

    private fun stopInternal() {
        val target = out
        runCatching { manager?.stop() }
        lastStarted = -1
        runCatching { target?.close() }
        if (out === target) out = null
    }

    override fun setRate(rate: Int) {
        this.rate = rate.coerceIn(0, 100)
        Log.i("$TAG 语速调整为 ${this.rate}")
    }

    override fun release() {
        stop()
        runCatching { manager?.release() }
        manager = null
    }
}
