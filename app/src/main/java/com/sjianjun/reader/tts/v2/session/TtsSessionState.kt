package com.sjianjun.reader.tts.v2.session

/**
 * 朗读会话模式
 */
enum class TtsSessionMode {
    /** 没有会话 */
    NONE,

    /** 小说：多章节，读完自动进入下一章 */
    BOOK,

    /** 网页：只读传入的正文，读完即停 */
    PAGE,
}

/**
 * 朗读会话状态。
 *
 * 会话层（[TtsPlayer]）唯一写入；交互层、通知层只读订阅。
 * 引擎层的段内进度只做"当前段"的展示，章节进度由会话层按段落位置换算。
 */
data class TtsSessionState(
    val mode: TtsSessionMode = TtsSessionMode.NONE,
    val playing: Boolean = false,
    /** 小说模式：书籍标识 */
    val bookId: String = "",
    val bookTitle: String = "",
    /** 当前章节序号（网页模式固定 0） */
    val chapterIndex: Int = -1,
    val chapterTitle: String = "",
    /** 网页模式：当前页面链接，作为会话标识 */
    val pageUrl: String = "",
    /** 当前朗读段落序号（对应章节段落列表下标） */
    val paragraphIndex: Int = 0,
    val paragraphCount: Int = 0,
    val paragraphText: String = "",
    /**
     * 起始段落的朗读起点偏移（字符数，0 表示从段落开头读）。
     *
     * 「从本页第一行开始读」时，起始段落会跳过本页之前的部分（上一页已显示过），
     * 阅读页据此把高亮落到本页第一行，而不是翻回上一页。
     */
    val paragraphStartOffset: Int = 0,
    /** 当前段内进度 0f~1f（引擎回传） */
    val paragraphProgress: Float = 0f,
    /** 本章进度 0~100（按段落位置换算） */
    val chapterProgressPercent: Int = 0,
    /** 本章已朗读到的预估位置（毫秒）：媒体卡片/通知的时间与进度条靠它 */
    val chapterPositionMs: Long = 0L,
    /** 本章预估总时长（毫秒）：媒体卡片/通知的时长靠它 */
    val chapterDurationMs: Long = 0L,
    /** 定时停止剩余分钟（0 表示未设置） */
    val sleepTimerMinutes: Int = 0,
    /** 定时停止截止时间戳 */
    val sleepTimerEndAt: Long = 0L,
    /** 定时到点后读完本章再停 */
    val stopAfterChapter: Boolean = false,
    /** 上次操作的错误信息（null 表示无错误） */
    val error: String? = null,
) {
    val active: Boolean get() = mode != TtsSessionMode.NONE
}
