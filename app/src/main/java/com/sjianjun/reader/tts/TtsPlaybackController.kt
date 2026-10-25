package com.sjianjun.reader.tts

import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.tts.v2.TtsPlayerHolder
import com.sjianjun.reader.tts.v2.notify.TtsNotificationService
import com.sjianjun.reader.tts.v2.session.TtsPlayer
import com.sjianjun.reader.tts.v2.session.TtsSessionMode
import com.sjianjun.reader.tts.v2.session.TtsSessionState
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sjj.alog.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 朗读会话的进程内入口（**对外契约不变**：状态 + 命令）。
 *
 * 内部已切换为新实现：
 * - 会话编排：[com.sjianjun.reader.tts.v2.session.TtsPlayer]（经 [TtsPlayerHolder] 持有）
 * - 引擎：系统 TTS / Edge（由配置决定）
 * - 通知/媒体/焦点：独立的 [TtsNotificationService] 前台服务
 *
 * 这里只做三件事：
 * 1. 命令转发；
 * 2. 把新会话状态映射成 [TtsPlaybackState] 供现有界面（播放卡片、跟随高亮、定时对话框）使用；
 * 3. 阅读页不在前台时回写阅读进度。
 */
object TtsPlaybackController {

    private val _state = MutableLiveData(TtsPlaybackState())

    /** 朗读会话状态（新会话状态映射而来），只有播放器写入 */
    val state: LiveData<TtsPlaybackState> get() = _state

    /** 阅读页是否在前台：在前台时阅读进度由阅读页写，这里不写，避免互相覆盖 */
    @Volatile
    private var readerVisible = false

    private val bridgeStarted = AtomicBoolean(false)

    @Volatile
    private var bookCover: String = ""

    /** 上次回写阅读进度的章节，避免每次进度刷新都写库 */
    private var lastRecordedChapter = -1

    private fun dispatchState(newState: TtsPlaybackState) {
        if (_state.value == newState) return
        // 服务回调都在主线程，直接 setValue 让 isActive()/isPlaying() 立刻拿到最新状态
        if (isMainThread()) {
            _state.value = newState
        } else {
            _state.postValue(newState)
        }
    }

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    /** 是否有任意朗读会话 */
    fun isActive(): Boolean = _state.value?.active == true

    fun isPlaying(): Boolean = _state.value?.playing == true

    /** 指定书籍是否正在朗读 */
    fun isActiveForBook(bookId: String): Boolean {
        val current = _state.value ?: return false
        return current.active && current.mode == TtsPlaybackMode.BOOK && current.bookId == bookId
    }

    /** 指定网页是否正在朗读 */
    fun isActiveForPage(pageUrl: String): Boolean {
        val current = _state.value ?: return false
        return current.active && current.mode == TtsPlaybackMode.PAGE && current.pageUrl == pageUrl
    }

    // ==================== 命令 ====================

    /**
     * 小说模式：开始朗读指定书籍的指定章节。
     *
     * @param paragraphIndex 阅读页传入的"本页第一行所在段落"的索引
     * @param paragraphOffset 本页第一行在该段落文本里的字符偏移：
     *        从**本页第一行**开始读，跨页段落的前半部分（上一页已显示过的）不读
     */
    fun startBook(bookId: String, chapterIndex: Int, paragraphIndex: Int = 0, paragraphOffset: Int = 0) {
        if (bookId.isBlank() || chapterIndex < 0) return
        // 设置里换了引擎（系统/Edge）：开始新的朗读前切过来
        TtsPlayerHolder.ensureConfiguredEngine()
        App.app.appScope.launch {
            val book = runCatching { withContext(Dispatchers.IO) { DbFactory.db.bookDao().getBookById(bookId) } }
                .getOrNull()
            bookCover = book?.cover.orEmpty()
            lastRecordedChapter = -1
            player().startBook(bookId, book?.title.orEmpty(), chapterIndex, paragraphIndex, paragraphOffset)
            TtsNotificationService.start(App.app)
        }
    }

    /**
     * 网页模式：只朗读当前页面正文，读完停止，不自动跳章。
     *
     * @param paragraphIndex 本页第一行所在段落（同 [startBook]）
     * @param paragraphOffset 本页第一行在该段落文本里的字符偏移（同 [startBook]）
     */
    fun startPage(
        pageUrl: String,
        title: String,
        text: String,
        paragraphIndex: Int = 0,
        paragraphOffset: Int = 0,
    ) {
        if (pageUrl.isBlank() || text.isBlank()) {
            Log.e("TtsPlaybackController 网页朗读参数不合法 url=$pageUrl textLen=${text.length}")
            return
        }
        TtsPlayerHolder.ensureConfiguredEngine()
        player().startPage(pageUrl, title, text, paragraphIndex, paragraphOffset)
        TtsNotificationService.start(App.app)
    }

    fun pause() = player().pause()

    fun resume() = player().resume()

    fun stop() = player().stop()

    fun toggle() {
        val p = player()
        if (p.state.value.playing) p.pause() else p.resume()
    }

    fun prevChapter() = player().prevChapter()

    fun nextChapter() = player().nextChapter()

    /**
     * 实时调整语速（0~100）：写入配置并立即生效（正在朗读时从当前段落重读）。
     *
     * 朗读设置里的拖动条可直接调用，不需要重启朗读。
     */
    fun setRate(rate: Int) {
        val value = rate.coerceIn(0, 100)
        globalConfig.readerTtsRate.postValue(value)
        player().setRate(value)
    }

    /**
     * 切换 Edge 音色（播放设置弹窗里改音色时调用）。
     *
     * 音色是合成会话级参数，播放中必须重建播放器，并从当前段落继续朗读；
     * 未在播放时只写配置，下次开始朗读生效。
     */
    fun applyVoice(voice: String) {
        if (voice.isBlank()) return
        if (voice == globalConfig.readerTtsVoice.value) return
        globalConfig.readerTtsVoice.setValue(voice)
        val current = TtsPlayerHolder.state.value
        if (!current.active) return
        TtsPlayerHolder.ensureConfiguredEngine()
        when (current.mode) {
            TtsSessionMode.BOOK -> startBook(
                current.bookId,
                current.chapterIndex,
                current.paragraphIndex,
                current.paragraphStartOffset,
            )

            TtsSessionMode.PAGE -> toast("音色已切换，重新播放后生效")
            TtsSessionMode.NONE -> Unit
        }
    }

    fun setSleepTimer(minutes: Int) = player().setSleepTimer(minutes)

    fun cycleSleepTimer() {
        val minutes = _state.value?.sleepTimerMinutes ?: 0
        player().setSleepTimer(nextSleepTimerMinutes(minutes))
    }

    /**
     * 定时到点后是否读完当前章节再停止
     */
    fun setStopAfterChapter(enabled: Boolean) = player().setStopAfterChapter(enabled)

    /** 结束正在进行的朗读（开始新的朗读会话前调用，保证不会有两个会话同时发声） */
    fun stopIfActive() {
        if (!isActive()) return
        player().stop()
    }

    fun setReaderVisible(visible: Boolean) {
        readerVisible = visible
    }

    fun isReaderVisible(): Boolean = readerVisible

    // ==================== 内部：新状态 → 旧状态 ====================

    init {
        // 只要控制器被使用就订阅状态：订阅的是"当前播放器"的稳定入口，
        // 引擎切换导致播放器重建后仍然能继续收到状态（否则界面不刷新、按钮状态不切换）
        startBridge()
    }

    private fun player(): TtsPlayer = TtsPlayerHolder.player()

    private fun startBridge() {
        if (!bridgeStarted.compareAndSet(false, true)) return
        // 兜底：若在 Application 初始化完成前就被访问，下次访问时重试绑定
        val scope = runCatching { App.app.appScope }.getOrElse {
            bridgeStarted.set(false)
            return
        }
        scope.launch {
            TtsPlayerHolder.state.collect { sessionState -> onSessionState(sessionState) }
        }
    }

    private fun onSessionState(sessionState: TtsSessionState) {
        dispatchState(sessionState.toPlaybackState())
        sessionState.error?.let { notifyError(it) }
        saveRecordIfNeeded(sessionState)
    }

    /** 已经提示过的错误，避免同一错误重复弹 */
    private var lastNotifiedError: String? = null

    /**
     * 朗读失败提示（合成失败/网络合成失败等）。
     *
     * 会话层只产出错误信息，提示放到这里，避免把 Toast 散落到引擎/会话里；
     * 阅读页不在前台时同样能提示（例如后台朗读时 Edge 网络失败）。
     */
    private fun notifyError(message: String) {
        if (message.isBlank() || message == lastNotifiedError) return
        lastNotifiedError = message
        App.app.appScope.launch(Dispatchers.Main) {
            runCatching { Toast.makeText(App.app, message, Toast.LENGTH_LONG).show() }
        }
    }

    private fun TtsSessionState.toPlaybackState(): TtsPlaybackState = TtsPlaybackState(
        active = active,
        playing = playing,
        mode = when (mode) {
            TtsSessionMode.NONE -> TtsPlaybackMode.NONE
            TtsSessionMode.BOOK -> TtsPlaybackMode.BOOK
            TtsSessionMode.PAGE -> TtsPlaybackMode.PAGE
        },
        bookId = bookId,
        bookTitle = bookTitle,
        bookCover = bookCover,
        pageUrl = pageUrl,
        chapterIndex = chapterIndex,
        // 章节链接用于目录刷新后的定位；新链路按章节序号跟随，留空即可（阅读页会回退到序号）
        chapterUrl = "",
        chapterTitle = chapterTitle,
        paragraphIndex = paragraphIndex,
        paragraphCount = paragraphCount,
        paragraphText = paragraphText,
        // 从本页第一行开始读：起始段带段内偏移，阅读页据此把高亮落在本页第一行
        paragraphStartOffset = paragraphStartOffset,
        paragraphProgress = paragraphProgress,
        chapterProgressPercent = chapterProgressPercent,
        // 旧字段是"本章预估总时长"，界面（播放卡片）用它显示时间轴
        chapterEstimateMs = chapterDurationMs,
        sleepTimerEndAt = sleepTimerEndAt,
        sleepTimerMinutes = sleepTimerMinutes,
        stopAfterChapter = stopAfterChapter,
        stopPendingAfterChapter = false,
    )

    /**
     * 阅读页不在前台时由这里回写阅读进度（阅读页在前台时由阅读页自己写，避免互相覆盖）
     */
    private fun saveRecordIfNeeded(sessionState: TtsSessionState) {
        if (sessionState.mode != TtsSessionMode.BOOK) return
        if (!sessionState.active || sessionState.chapterIndex < 0) return
        if (readerVisible) return
        if (sessionState.chapterIndex == lastRecordedChapter) return
        lastRecordedChapter = sessionState.chapterIndex
        val bookId = sessionState.bookId
        val title = sessionState.bookTitle
        if (bookId.isBlank() || title.isBlank()) return
        App.app.appScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = DbFactory.db.readingRecordDao()
                val record = dao.getReadingRecordSync(title) ?: ReadingRecord(title, bookId)
                record.bookId = bookId
                record.chapterIndex = sessionState.chapterIndex
                record.offest = 0
                record.scrollOffset = 0
                record.isEnd = false
                record.updateTime = System.currentTimeMillis()
                dao.insertReadingRecord(record)
                Log.i("回写阅读进度：$title 第${sessionState.chapterIndex + 1}章")
            }.onFailure { Log.e("回写阅读进度失败：$title", it) }
        }
    }

    private fun nextSleepTimerMinutes(minutes: Int): Int {
        val options = intArrayOf(15, 30, 60)
        if (minutes <= 0) return options.first()
        val nextIndex = options.indexOf(minutes) + 1
        return options.getOrNull(nextIndex) ?: 0
    }
}
