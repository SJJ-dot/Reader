package com.sjianjun.reader.utils

import androidx.lifecycle.MutableLiveData
import sjj.novel.view.reader.page.TxtLine
import sjj.novel.view.reader.page.TxtPage

/**
 * 朗读单元：一段待朗读的文本 + 进度标识。
 * - 逐行模式（阅读页/网页阅读模式）：tag 是排版后的 [TxtLine]
 * - 段落模式（前台朗读服务）：tag 是段落序号 [Int]
 */
class TtsSpeakLine(@JvmField val txt: String, @JvmField val tag: Any)

/**
 * Common TTS interface implemented by different TTS engines.
 */
interface BaseTts {
    val isSpeaking: MutableLiveData<Boolean>

    /**
     * 逐行进度回调（逐行模式），参数是排版后的行
     */
    var progressChangeCallback: ((TxtLine) -> Unit)?

    /**
     * 段落进度回调（段落模式），参数是段落序号
     */
    var paragraphChangeCallback: ((Int) -> Unit)?

    /**
     * 段内真实朗读进度回调 0f~1f（只用于段落模式）。
     *
     * 语音引擎能拿到真实播放位置时回调：系统 TTS 用「已朗读到的字符位置」，
     * Edge 用「音频播放位置 / 音频总时长」。
     * 引擎拿不到（不支持范围回调、时长未知）时不回调，调用方用时间模型兜底估算。
     */
    var segmentProgressCallback: ((Float) -> Unit)?

    /**
     * 引擎真正开始出声时回调（每段开始播放时触发一次）。
     *
     * 用于把时间模型起点对齐到「音频真正开始播放」的时刻，
     * 避免引擎在合成/缓冲期间（还没出声）时间模型就提前走进度。
     * 系统 TTS 在 UtteranceProgressListener.onStart 时触发，
     * Edge 在 MediaPlayer onPrepared 后 start() 时触发。
     */
    var onPlaybackStarted: (() -> Unit)?

    /**
     * 逐行模式朗读完成
     */
    var onCompleted: (() -> Unit)?

    /**
     * 段落模式朗读完成
     */
    var onParagraphsCompleted: (() -> Unit)?

    /**
     * 逐行朗读：直接使用阅读页排版好的页面数据
     * @param pages 从当前页开始的页面列表
     * @param pagePos 起始页
     */
    suspend fun start(pages: List<TxtPage>?, pagePos: Int)

    /**
     * 段落朗读：只依赖纯文本，供前台朗读服务在脱离阅读页（没有排版数据）时使用。
     * 与 [start] 二选一，最后一次调用的模式生效。
     * @param paragraphs 整章段落文本
     * @param startIndex 从第几段（含）开始朗读
     * @return 是否成功开始朗读（引擎初始化失败等返回 false）
     */
    suspend fun startParagraphs(paragraphs: List<String>, startIndex: Int): Boolean

    fun stop()

    /**
     * Release the underlying TTS engine instance. A subsequent [start] will create a new engine.
     */
    fun release()

}
