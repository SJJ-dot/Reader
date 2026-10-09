package com.sjianjun.reader.tts

/**
 * 朗读会话模式
 */
enum class TtsPlaybackMode {
    /** 没有会话 */
    NONE,

    /** 小说阅读器：多章节，读完自动进入下一章，支持上一章/下一章 */
    BOOK,

    /** 网页阅读模式：只朗读当前页面正文，读完停止，不自动跳章 */
    PAGE,
}

/**
 * 朗读会话状态。
 *
 * 只有 [TtsPlaybackService] 会写入，阅读页 / 网页阅读页 / 朗读设置页 / 媒体通知只读取，
 * 避免多方各自维护一份状态导致不一致。
 */
data class TtsPlaybackState(
    /** 是否存在朗读会话（暂停中也算） */
    val active: Boolean = false,
    val playing: Boolean = false,
    val mode: TtsPlaybackMode = TtsPlaybackMode.NONE,
    /** 小说模式：书籍标识 */
    val bookId: String = "",
    val bookTitle: String = "",
    /** 书籍封面（本地路径或网络地址），用作播放器上的封面图 */
    val bookCover: String = "",
    /** 网页模式：当前页面链接，作为会话标识 */
    val pageUrl: String = "",
    /** 正在朗读的章节序号（对应 Chapter.index），网页模式固定为 0 */
    val chapterIndex: Int = -1,
    /** 正在朗读的章节链接，目录刷新后章节序号可能变化，用链接兜底定位 */
    val chapterUrl: String = "",
    val chapterTitle: String = "",
    /** 正在朗读的段落序号，与 [TtsChapterSplitter.split] 的结果一一对应 */
    val paragraphIndex: Int = 0,
    val paragraphCount: Int = 0,
    /** 正在朗读的段落文本，阅读页定位段落失败时用它兜底 */
    val paragraphText: String = "",
    /** 当前段落开始朗读的时间戳，用于按时间估算段内进度 */
    val paragraphStartAt: Long = 0L,
    /** 当前段落预估朗读时长（毫秒），用于按时间估算段内进度 */
    val paragraphEstimateMs: Long = 0L,
    /**
     * 当前段落已朗读进度 0f~1f。
     * 优先用语音引擎回传的真实朗读位置，引擎拿不到时按时间估算；节流上报，阅读器据此按行高亮
     */
    val paragraphProgress: Float = 0f,
    /** 本章预估朗读进度 0~100（按「章节段落权重 + 段落序号 + 段内进度」估算） */
    val chapterProgressPercent: Int = 0,
    /** 本章预估朗读总时长（毫秒），用于播放器进度条 */
    val chapterEstimateMs: Long = 0L,
    /** 定时停止的截止时间戳，0 表示未设置 */
    val sleepTimerEndAt: Long = 0L,
    /** 定时停止选择的时长（分钟），0 表示未设置 */
    val sleepTimerMinutes: Int = 0,
    /** 定时到点后是否读完当前章节再停止 */
    val stopAfterChapter: Boolean = false,
    /** 定时已经到点，正在等当前章节读完再停 */
    val stopPendingAfterChapter: Boolean = false,
) {
    /** 定时停止剩余分钟数，未设置时为 0 */
    val sleepTimerRemainingMinutes: Int
        get() {
            if (sleepTimerEndAt <= 0L) return 0
            val remain = sleepTimerEndAt - System.currentTimeMillis()
            if (remain <= 0L) return 0
            return ((remain + 59_999L) / 60_000L).toInt()
        }
}

/**
 * 段落内预估进度 0f~1f：按「段落开始时间 + 段落预估时长」估算。
 * 只在引擎拿不到真实朗读位置时作为兜底；暂停时保持最后一次上报的值。
 */
fun TtsPlaybackState.estimateParagraphRatio(): Float {
    // 暂停/没在朗读时保持最后一次上报的值，不要在暂停期间继续增长
    if (!playing || paragraphStartAt <= 0L || paragraphEstimateMs <= 0L) return paragraphProgress
    val elapsed = System.currentTimeMillis() - paragraphStartAt
    return (elapsed.toFloat() / paragraphEstimateMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * 定时停止按钮文案：提示下一次点击会切到什么，顺序为 关闭 -> 15 -> 30 -> 60 -> 关闭
 */
fun sleepTimerActionTitle(minutes: Int): String = when (minutes) {
    15 -> "定时30分钟"
    30 -> "定时60分钟"
    60 -> "取消定时"
    else -> "定时停止"
}
