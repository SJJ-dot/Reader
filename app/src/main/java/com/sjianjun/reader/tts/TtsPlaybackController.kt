package com.sjianjun.reader.tts

import android.content.Intent
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.App
import sjj.alog.Log

/**
 * 朗读会话的进程内入口。
 *
 * - 对上层（小说阅读页、网页阅读页、朗读设置页、通知、媒体按键）只暴露「状态 + 命令」
 * - 状态由 [TtsPlaybackService] 唯一写入，这里只做分发
 * - 命令优先同进程直调服务实例，服务还没创建时退回 Intent
 */
object TtsPlaybackController {

    private val _state = MutableLiveData(TtsPlaybackState())

    /** 朗读会话状态，只有服务写入 */
    val state: LiveData<TtsPlaybackState> get() = _state

    @Volatile
    private var service: TtsPlaybackService? = null

    /** 阅读页是否在前台：在前台时阅读进度由阅读页写，服务不写，避免互相覆盖 */
    @Volatile
    private var readerVisible = false

    /** 网页模式待朗读正文：同进程直接传递，避免大文本走 Intent 事务限制 */
    @Volatile
    internal var pendingPageText: String = ""

    internal fun attachService(newService: TtsPlaybackService?) {
        service = newService
    }

    internal fun dispatchState(newState: TtsPlaybackState) {
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

    /**
     * 小说模式：开始朗读指定书籍的指定章节，从第 paragraphIndex 段开始，读完自动进入下一章
     */
    fun startBook(bookId: String, chapterIndex: Int, paragraphIndex: Int = 0) {
        val target = service
        if (target != null) {
            target.startBookSession(bookId, chapterIndex, paragraphIndex)
            return
        }
        val intent = baseIntent(TtsPlaybackService.ACTION_START_BOOK)
            .putExtra(TtsPlaybackService.EXTRA_BOOK_ID, bookId)
            .putExtra(TtsPlaybackService.EXTRA_CHAPTER_INDEX, chapterIndex)
            .putExtra(TtsPlaybackService.EXTRA_PARAGRAPH_INDEX, paragraphIndex)
        startForegroundService(intent)
    }

    /**
     * 网页模式：只朗读当前页面正文，读完停止，不自动跳章。
     * 正文内容每次都取新的，所以即使服务已在朗读也会重开会话。
     */
    fun startPage(pageUrl: String, title: String, text: String, paragraphIndex: Int = 0) {
        if (pageUrl.isBlank() || text.isBlank()) {
            Log.e("TtsPlaybackController 网页朗读参数不合法 url=$pageUrl textLen=${text.length}")
            return
        }
        val target = service
        if (target != null) {
            target.startPageSession(pageUrl, title, text, paragraphIndex)
            return
        }
        pendingPageText = text
        val intent = baseIntent(TtsPlaybackService.ACTION_START_PAGE)
            .putExtra(TtsPlaybackService.EXTRA_PAGE_URL, pageUrl)
            .putExtra(TtsPlaybackService.EXTRA_PAGE_TITLE, title)
            .putExtra(TtsPlaybackService.EXTRA_PARAGRAPH_INDEX, paragraphIndex)
        if (!startForegroundService(intent)) {
            // 启动失败时清掉暂存正文，避免残留给下一次启动
            pendingPageText = ""
        }
    }

    fun pause() = dispatch(TtsPlaybackService.ACTION_PAUSE) { it.pausePlayback() }

    fun resume() = dispatch(TtsPlaybackService.ACTION_RESUME) { it.resumePlayback() }

    fun stop() = dispatch(TtsPlaybackService.ACTION_STOP) { it.stopSession() }

    fun toggle() = dispatch(TtsPlaybackService.ACTION_TOGGLE) {
        if (it.isPlayingNow()) it.pausePlayback() else it.resumePlayback()
    }

    fun prevChapter() = dispatch(TtsPlaybackService.ACTION_PREV) { it.prevChapter() }

    fun nextChapter() = dispatch(TtsPlaybackService.ACTION_NEXT) { it.nextChapter() }

    fun setSleepTimer(minutes: Int) {
        val target = service
        if (target != null) {
            target.setSleepTimer(minutes)
        } else {
            send(TtsPlaybackService.ACTION_SET_TIMER, minutes)
        }
    }

    fun cycleSleepTimer() = dispatch(TtsPlaybackService.ACTION_CYCLE_TIMER) { it.cycleSleepTimer() }

    /**
     * 定时到点后是否读完当前章节再停止
     */
    fun setStopAfterChapter(enabled: Boolean) {
        val target = service
        if (target != null) {
            target.setStopAfterChapter(enabled)
            return
        }
        val intent = baseIntent(TtsPlaybackService.ACTION_SET_STOP_AFTER_CHAPTER)
            .putExtra(TtsPlaybackService.EXTRA_STOP_AFTER_CHAPTER, enabled)
        try {
            App.app.startService(intent)
        } catch (e: Throwable) {
            Log.e("TtsPlaybackController 设置读完本章再停失败", e)
        }
    }

    /**
     * 结束正在进行的朗读（同进程同步执行）。
     * 开始新的朗读会话前会调用，保证不会有两个会话同时发声。
     */
    fun stopIfActive() {
        if (!isActive()) return
        val target = service
        if (target != null) {
            target.stopSession()
        } else {
            send(TtsPlaybackService.ACTION_STOP)
        }
    }

    fun setReaderVisible(visible: Boolean) {
        readerVisible = visible
    }

    fun isReaderVisible(): Boolean = readerVisible

    private inline fun dispatch(action: String, direct: (TtsPlaybackService) -> Unit) {
        val target = service
        if (target != null) {
            direct(target)
        } else {
            send(action)
        }
    }

    private fun baseIntent(action: String) = Intent(App.app, TtsPlaybackService::class.java).setAction(action)

    private fun startForegroundService(intent: Intent): Boolean {
        return try {
            ContextCompat.startForegroundService(App.app, intent)
            true
        } catch (e: Throwable) {
            Log.e("TtsPlaybackController 启动朗读失败", e)
            false
        }
    }

    private fun send(action: String, minutes: Int? = null) {
        val intent = baseIntent(action)
        if (minutes != null) {
            intent.putExtra(TtsPlaybackService.EXTRA_MINUTES, minutes)
        }
        try {
            App.app.startService(intent)
        } catch (e: Throwable) {
            Log.e("TtsPlaybackController 发送命令失败 action=$action", e)
        }
    }
}
