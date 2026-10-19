package com.sjianjun.reader.tts.v2

import com.sjianjun.reader.tts.TtsChapterSplitter

/**
 * 段落朗读时间模型：**每个引擎一份实例，参数按各引擎实测设定**。
 *
 * 放在引擎内部而不是会话层，原因有两个：
 * 1. 两段之间「合成/准备下一段音频」的时间段，进度应该显示为下一段的 0%，
 *    这个切换点只有引擎自己知道；
 * 2. 不同引擎的每字耗时差别很大（系统 TTS 受本机引擎影响，Edge 是固定码率音频），
 *    各自校准互不干扰。
 *
 * 只做纯计算：段落开始 → 按权重估算耗时 → 读取比例；段落读完用真实耗时校准。
 */
class ParagraphTimeModel(
    /** 每单位朗读权重的基准耗时（毫秒），各引擎不同 */
    private val baseMsPerWeight: Long = 200L,
    /** 实测值的合理范围，超出视为异常样本 */
    private val minMsPerWeight: Long = 60L,
    private val maxMsPerWeight: Long = 600L,
) {

    private var weight = 0L
    private var startedAt = 0L
    private var ratioFloor = 0f
    private var msPerWeight = baseMsPerWeight

    /** 当前实测的「每权重耗时」，供测试/日志使用 */
    val currentMsPerWeight: Long get() = msPerWeight

    /** 段落朗读权重：与阅读页高亮共用同一口径，保证两侧进度一致 */
    fun weightOf(text: CharSequence?): Long = TtsChapterSplitter.speakingWeight(text)

    /** 开始朗读一段：进度从 0 开始（两段之间的准备时间计入下一段的 0%） */
    fun start(text: CharSequence?) {
        weight = weightOf(text)
        startedAt = System.currentTimeMillis()
        ratioFloor = 0f
    }

    /** 当前段已朗读比例 0f..1f：单调不回退 */
    fun ratio(): Float {
        if (startedAt <= 0L || weight <= 0L) return 0f
        val elapsed = System.currentTimeMillis() - startedAt
        val estimate = weight * msPerWeight
        if (estimate <= 0L) return 0f
        val value = (elapsed.toFloat() / estimate.toFloat()).coerceIn(0f, 1f)
        if (value > ratioFloor) ratioFloor = value
        return ratioFloor
    }

    /** 段落读完：用真实耗时（扣除停顿后仍偏大，做平滑）校准每权重耗时 */
    fun finishAndCalibrate() {
        if (startedAt <= 0L || weight <= 0L) return
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed <= 0L) return
        val sample = (elapsed / weight).coerceIn(minMsPerWeight, maxMsPerWeight)
        msPerWeight = (msPerWeight * 2 + sample) / 3
        startedAt = 0L
    }

    /** 丢弃当前段状态（停止/取消时调用） */
    fun reset() {
        startedAt = 0L
        weight = 0L
        ratioFloor = 0f
    }
}
