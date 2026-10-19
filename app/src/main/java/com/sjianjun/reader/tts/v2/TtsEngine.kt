package com.sjianjun.reader.tts.v2

import kotlinx.coroutines.flow.Flow

/**
 * 朗读引擎类型
 */
enum class TtsEngineType(val label: String) {
    /** 系统 TTS（设备自带语音引擎） */
    SYSTEM("系统 TTS"),

    /** 微软 Edge 在线语音 */
    EDGE("Edge TTS"),
}

/**
 * 段落朗读事件。
 *
 * 引擎只负责「接收文本 → 合成 → 播放 → 回传进度」，不关心会话、章节、定时、通知。
 */
sealed interface TtsEngineEvent {

    /** 开始朗读某一段（index 是传入 paragraphs 的下标；空段不会产生事件） */
    data class ParagraphStarted(val index: Int) : TtsEngineEvent

    /**
     * 本段朗读进度 0f..1f。
     *
     * 由引擎自己实现：有的引擎能拿到真实播放位置（Edge 的播放器位置/时长），
     * 有的只能按时间估算（系统 TTS），两种都要保证「同一段内只前进不回退」，
     * 且「进入下一段时从 0 开始」——两段之间合成/准备音频的时间属于下一段的 0%。
     */
    data class SegmentProgress(val index: Int, val ratio: Float) : TtsEngineEvent

    /** 本段朗读结束 */
    data class ParagraphFinished(val index: Int) : TtsEngineEvent

    /** 全部段落朗读完成 */
    object Completed : TtsEngineEvent

    /** 朗读失败（引擎初始化失败、合成/播放异常等） */
    data class Failed(val error: Throwable) : TtsEngineEvent
}

/**
 * TTS 播放引擎：只做「文本 → 语音 → 进度」。
 *
 * **引擎的处理单位是段落，不是章节**：一次 [speak] 开启一条"段落流"，
 * 后续段落用 [append] 追加（章节只存在于会话层，引擎不做任何章节相关处理）。
 *
 * - 顺序播放：`append` 只往队尾追加，不重建事件流、不重置队列与段内进度；
 * - 跳章/定位：`stop()` + `speak(新列表, 起始段)`，队列、已合成音频、时间模型一起重置；
 * - 全书读完：`finish()`，队列排空后发 [TtsEngineEvent.Completed] 并结束事件流。
 *
 * 队列、合成线程、播放线程、段内进度模型都由各引擎自己实现（系统 TTS 与 Edge 的实现和参数不同）。
 */
interface TtsEngine {

    val type: TtsEngineType

    /**
     * 合成领先窗口（段数）：会话层据此在"离本章末尾不足 [lookahead] 段"时，
     * 把下一章的段落 [append] 进来，保证顺序播放时章与章之间不断流。
     *
     * 系统 TTS 一次只能提交一段（边合成边播），取 1；Edge 内部会预合成多段，取 5。
     */
    val lookahead: Int

    /**
     * 从 [paragraphs] 的第 [startIndex] 段（含）开始朗读，开启一条段落流。
     *
     * 事件里的 `index` 是**段落流内的扁平下标**：`paragraphs[i]` 对应 `i`，
     * 之后 [append] 进来的段落接着往后编号（会话层据此把下标映射回"章 / 段"）。
     *
     * @param endOfStream 这条流之后不会再有 [append]（网页模式"读完即停"），
     *                    队列排空直接发 [TtsEngineEvent.Completed]；小说模式传 false，
     *                    章末由会话层 [append] 续上，只有全书读完才 [finish]。
     */
    fun speak(paragraphs: List<String>, startIndex: Int = 0, endOfStream: Boolean = false): Flow<TtsEngineEvent>

    /**
     * 追加段落（下一章内容）：排到队尾顺序播放，**不重置**当前播放与进度。
     *
     * 段落流内扁平下标从上一个段落继续累加。
     */
    fun append(paragraphs: List<String>)

    /**
     * 没有更多段落了（全书读完）：队列排空后发 [TtsEngineEvent.Completed]。
     */
    fun finish()

    /**
     * 引擎实测的「每权重耗时」（毫秒）：引擎读完若干段后按真实耗时校准。
     *
     * 会话层用它估算章节时间轴（播放卡片/通知的时长与位置），比公式换算准；
     * 返回 <=0 表示还没有实测值（用公式兜底）。
     */
    val calibratedMsPerWeight: Long get() = -1L

    /** 停止当前朗读（幂等）：取消合成/播放、清空队列（下一次 [speak] 重新开始） */
    fun stop()

    /**
     * 调整语速（0~100，与设置里的口径一致）。
     *
     * 引擎只负责让"下一段"用新语速；要立刻听到变化，由会话层从当前段落重读
     * （系统 TTS 的 setSpeechRate 只对后续 utterance 生效，Edge 的 rate 在合成时生效）。
     */
    fun setRate(rate: Int)

    /** 释放引擎资源；再次 [speak] 会自动重新初始化 */
    fun release()
}
