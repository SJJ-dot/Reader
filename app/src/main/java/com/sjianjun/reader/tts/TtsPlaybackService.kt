package com.sjianjun.reader.tts

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.bumptech.glide.request.FutureTarget
import com.sjianjun.reader.App
import com.sjianjun.reader.BOOK_ID
import com.sjianjun.reader.GlideApp
import com.sjianjun.reader.SHOW_TTS_CARD
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReadingRecord
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.repository.ReplacementRuleUseCase
import com.sjianjun.reader.utils.ChapterTextPreparer
import com.sjianjun.reader.utils.TtsService
import com.sjianjun.reader.utils.format
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sjj.alog.Log

/**
 * 前台朗读服务：朗读会话的唯一主人。
 *
 * 与阅读界面完全解耦，自己负责：
 * 1. 小说模式：按 bookId 取目录与章节正文（数据库缓存优先），把正文分段后逐段朗读，
 *    一章读完自动进入下一章，朗读时后台预加载下一章，并把阅读进度写回数据库
 * 2. 网页模式：只朗读调用方传入的当前页面正文，读完停止，不自动跳章
 * 3. 维护前台媒体通知、MediaSession（耳机/蓝牙/车机按键）、定时停止
 * 4. 按「章节段落权重 + 段落序号 + 段落已读时间」估算并回传本章朗读进度
 *
 * 不持有任何 Activity / PageLoader 引用，退出阅读页面后仍在后台继续朗读。
 */
class TtsPlaybackService : Service() {

    companion object {
        private const val TAG = "TtsPlaybackService"

        const val ACTION_START_BOOK = "com.sjianjun.reader.tts.START_BOOK"
        const val ACTION_START_PAGE = "com.sjianjun.reader.tts.START_PAGE"
        const val ACTION_TOGGLE = "com.sjianjun.reader.tts.TOGGLE"
        const val ACTION_PAUSE = "com.sjianjun.reader.tts.PAUSE"
        const val ACTION_RESUME = "com.sjianjun.reader.tts.RESUME"
        const val ACTION_STOP = "com.sjianjun.reader.tts.STOP"
        const val ACTION_PREV = "com.sjianjun.reader.tts.PREV"
        const val ACTION_NEXT = "com.sjianjun.reader.tts.NEXT"
        const val ACTION_SET_TIMER = "com.sjianjun.reader.tts.SET_TIMER"
        const val ACTION_CYCLE_TIMER = "com.sjianjun.reader.tts.CYCLE_TIMER"
        const val ACTION_SET_STOP_AFTER_CHAPTER = "com.sjianjun.reader.tts.SET_STOP_AFTER_CHAPTER"

        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_CHAPTER_INDEX = "extra_chapter_index"
        const val EXTRA_PARAGRAPH_INDEX = "extra_paragraph_index"
        const val EXTRA_PAGE_URL = "extra_page_url"
        const val EXTRA_PAGE_TITLE = "extra_page_title"
        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_STOP_AFTER_CHAPTER = "extra_stop_after_chapter"

        /** 定时停止可选时长（分钟），按顺序切换：关闭 -> 15 -> 30 -> 60 -> 关闭 */
        private val SLEEP_TIMER_MINUTES = intArrayOf(15, 30, 60)

        /** 单章最多拉取的分页数，避免异常数据把分页循环卡死 */
        private const val MAX_PAGE_LOAD = 60

        /** 连续多少章内容加载失败就结束朗读，避免在坏数据上反复请求 */
        private const val MAX_FAILED_CHAPTER = 3

        private const val WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1000L
        private const val TIMER_TICK_MS = 10_000L

        /** 进度估算刷新间隔（引擎没有真实进度回传时的兜底采样），只有整数百分比变化时才刷新状态 */
        private const val PROGRESS_TICK_MS = 500L

        /** 每单位朗读权重的基准耗时（毫秒），与语音引擎的耗时估算模型一致 */
        private const val BASE_MS_PER_WEIGHT = 200L

        /** 超过这么久没有收到引擎回传的真实段内进度，就认为引擎拿不到真实位置，改用时间模型兜底 */
        private const val ENGINE_PROGRESS_TIMEOUT_MS = 3_000L

        /** 段内进度上报的最小间隔：太密会频繁重建通知，太疏高亮会落后于语音 */
        private const val PROGRESS_REPORT_MIN_MS = 250L

        /** 实测「每权重耗时」的合理范围，超出视为异常样本 */
        private const val MIN_MS_PER_WEIGHT = 60L
        private const val MAX_MS_PER_WEIGHT = 600L

        /** 太短的段落里段落间隙占比高，测出来的时长不准，不参与校准 */
        private const val MIN_CALIBRATE_WEIGHT = 20L

        /** 段落开头异常字符检查长度 */
        private const val HEAD_CHECK_LENGTH = 8

        /** 播放器封面图尺寸：放进媒体元数据/通知的位图会跨进程拷贝，不能太大 */
        private const val COVER_SIZE = 192

        /** 连续多少次检测不到播放控制面板就认为播放器被移除了（配合进度轮询，约 2 秒） */
        private const val MISSING_NOTIFICATION_LIMIT = 4
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bookDao get() = DbFactory.db.bookDao()
    private val chapterDao get() = DbFactory.db.chapterDao()
    private val readingRecordDao get() = DbFactory.db.readingRecordDao()

    private lateinit var mediaSession: TtsMediaSessionHolder

    private var sessionJob: Job? = null
    private var timerJob: Job? = null
    private var progressJob: Job? = null
    private var prefetchJob: Job? = null
    private var coverJob: Job? = null

    /** 书籍封面位图，作为播放器（媒体卡片）左上角图标 */
    private var coverArtwork: Bitmap? = null

    /** 播放控制面板（通知/媒体卡片）是否出现过：出现过再消失，说明用户把播放器移除了 */
    private var notificationSeen = false

    /** 连续检测不到通知的次数，避免瞬时抖动造成误判 */
    private var notificationMissingCount = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var foregroundStarted = false

    /** 会话状态，只有本服务写入 */
    private var state = TtsPlaybackState()
    private var book: Book? = null
    private var chapterList: List<Chapter> = emptyList()
    private var paragraphs: List<String> = emptyList()

    /** 各段落的朗读权重，用于预估进度（按标点估算朗读耗时） */
    private var paragraphWeights = LongArray(0)
    private var totalWeight = 0L

    /** 预加载的下一章（小说模式），切章时直接使用 */
    private var prefetchedIndex = -1
    private var prefetchedParagraphs: List<String> = emptyList()

    /** 预加载代际号：取消/清理后旧协程的回写会被丢弃 */
    private var prefetchToken = 0

    private var pausedByFocus = false
    private var sleepTimerMinutes = 0

    /** 定时到点后是否读完当前章节再停 */
    private var stopAfterChapter = false

    /** 定时已到点，正在等当前章节读完再停 */
    private var stopPendingAfterChapter = false

    /** 引擎回传的真实段内进度 0f~1f；-1f 表示本段还没收到过真实进度（引擎回调在 binder 线程，故加 @Volatile） */
    @Volatile
    private var engineSegmentProgress = -1f

    /** 最近一次收到真实段内进度的时间戳 */
    @Volatile
    private var engineSegmentProgressAt = 0L

    /** 本段已经上报过的最大进度，保证同一段内进度只前进不回退 */
    private var paragraphRatioFloor = 0f

    /** 最近一次上报段内进度的时间戳，用于节流 */
    private var progressReportedAt = 0L

    /** 实测的「每权重耗时」（毫秒），同一语速下优先于固定模型 */
    private var measuredMsPerWeight = 0L

    /** 上面实测值对应的语速设置，语速一变就作废重新校准 */
    private var measuredMsPerWeightRate = -1

    /**
     * 朗读引擎的代际号：切换章节/暂停会自增，
     * 旧引擎迟到的进度或完成回调据此丢弃，避免跳章
     */
    private var engineGeneration = 0

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.i("$TAG 耳机拔出，暂停朗读")
                pausePlayback()
            }
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.i("$TAG 音频焦点丢失，停止朗读")
                stopSession()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedByFocus = state.playing
                Log.i("$TAG 音频焦点被临时占用，暂停朗读 pausedByFocus=$pausedByFocus")
                pausePlayback()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByFocus) {
                    pausedByFocus = false
                    Log.i("$TAG 音频焦点恢复，继续朗读")
                    resumePlayback()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        TtsPlaybackController.attachService(this)
        TtsNotificationHelper.ensureChannel(this)
        mediaSession = TtsMediaSessionHolder(this, object : TtsMediaSessionHolder.Callback {
            override fun onPlay() = resumePlayback()

            override fun onPause() = pausePlayback()

            override fun onStop() = stopSession()

            override fun onPrevChapter() = prevChapter()

            override fun onNextChapter() = nextChapter()

            override fun onReaderMenu() = openReaderMenu()
        })
        try {
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        } catch (e: Throwable) {
            Log.e("$TAG 注册耳机拔出监听失败", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BOOK -> startBookSession(
                intent.getStringExtra(EXTRA_BOOK_ID).orEmpty(),
                intent.getIntExtra(EXTRA_CHAPTER_INDEX, -1),
                intent.getIntExtra(EXTRA_PARAGRAPH_INDEX, 0)
            )

            ACTION_START_PAGE -> {
                val text = TtsPlaybackController.pendingPageText
                TtsPlaybackController.pendingPageText = ""
                startPageSession(
                    intent.getStringExtra(EXTRA_PAGE_URL).orEmpty(),
                    intent.getStringExtra(EXTRA_PAGE_TITLE).orEmpty(),
                    text,
                    intent.getIntExtra(EXTRA_PARAGRAPH_INDEX, 0)
                )
            }

            ACTION_TOGGLE -> if (state.playing) pausePlayback() else resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
            ACTION_STOP -> stopSession()
            ACTION_PREV -> prevChapter()
            ACTION_NEXT -> nextChapter()
            ACTION_SET_TIMER -> setSleepTimer(intent.getIntExtra(EXTRA_MINUTES, 0))
            ACTION_CYCLE_TIMER -> cycleSleepTimer()
            ACTION_SET_STOP_AFTER_CHAPTER ->
                setStopAfterChapter(intent.getBooleanExtra(EXTRA_STOP_AFTER_CHAPTER, false))
            else -> stopSelfIfIdle()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        teardown()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Throwable) {
        }
        mediaSession.release()
        scope.cancel()
        TtsPlaybackController.attachService(null)
        super.onDestroy()
    }

    // ==================== 会话控制（通知与媒体按键走 Intent，同进程调用直接调用方法） ====================

    /**
     * 小说模式：多章节朗读，读完自动进入下一章，朗读期间预加载下一章
     */
    fun startBookSession(bookId: String, chapterIndex: Int, paragraphIndex: Int) {
        if (bookId.isBlank() || chapterIndex < 0) {
            Log.e("$TAG 启动参数不合法 bookId=$bookId chapterIndex=$chapterIndex")
            stopSelfIfIdle()
            return
        }
        resetSession()
        updateState(
            TtsPlaybackState(
                active = true,
                playing = true,
                mode = TtsPlaybackMode.BOOK,
                bookId = bookId,
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex
            )
        )
        if (!startForegroundCompat()) return
        sessionJob = scope.launch {
            try {
                val loadedBook = withContext(Dispatchers.IO) { bookDao.getBookById(bookId) }
                if (loadedBook == null) {
                    Log.e("$TAG 未找到书籍 bookId=$bookId")
                    toast("朗读失败：未找到书籍")
                    stopSession()
                    return@launch
                }
                val loadedChapters = withContext(Dispatchers.IO) { chapterDao.getChapterListByBookId(bookId).first() }
                if (loadedChapters.isEmpty()) {
                    Log.e("$TAG 书籍没有章节 bookId=$bookId")
                    toast("朗读失败：书籍没有章节")
                    stopSession()
                    return@launch
                }
                book = loadedBook
                chapterList = loadedChapters
                updateState(state.copy(bookTitle = loadedBook.title, bookCover = loadedBook.cover.orEmpty()))
                // 封面作为播放器（媒体卡片）左上角图标
                loadCoverArtwork(loadedBook.cover)
                Log.i("$TAG 开始朗读《${loadedBook.title}》第${chapterIndex + 1}章 第${paragraphIndex + 1}段")
                playChapter(chapterIndex, paragraphIndex)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e("$TAG 启动朗读失败", e)
                toast("朗读失败：${e.message}")
                stopSession()
            }
        }
    }

    /**
     * 网页模式：只朗读传入的当前页面正文，读完停止，不自动跳章
     */
    fun startPageSession(pageUrl: String, title: String, text: String, paragraphIndex: Int) {
        if (pageUrl.isBlank() || text.isBlank()) {
            Log.e("$TAG 网页朗读参数不合法 url=$pageUrl textLen=${text.length}")
            stopSelfIfIdle()
            return
        }
        resetSession()
        val list = TtsChapterSplitter.split(title, text)
        if (list.isEmpty()) {
            Log.e("$TAG 网页正文分段为空 url=$pageUrl")
            stopSelfIfIdle()
            return
        }
        updateState(
            TtsPlaybackState(
                active = true,
                playing = true,
                mode = TtsPlaybackMode.PAGE,
                bookTitle = title.ifBlank { "网页阅读" },
                pageUrl = pageUrl,
                chapterIndex = 0,
                chapterUrl = pageUrl,
                chapterTitle = title,
                paragraphIndex = paragraphIndex.coerceIn(0, list.size - 1),
                paragraphCount = list.size
            )
        )
        if (!startForegroundCompat()) {
            toast("朗读启动失败：无法进入后台播放")
            teardown()
            stopSelf()
            return
        }
        prepareParagraphs(list)
        val start = state.paragraphIndex.coerceIn(0, list.size - 1)
        Log.i("$TAG 开始朗读网页《$title》共${list.size}段，从第${start + 1}段开始")
        acquireWakeLock()
        acquireAudioFocus()
        startEngine(start)
    }

    fun pausePlayback() {
        if (!state.active) {
            stopSelfIfIdle()
            return
        }
        if (!state.playing) return
        Log.i("$TAG 暂停朗读")
        stopEngine()
        releaseWakeLock()
        updateState(state.copy(playing = false))
    }

    fun resumePlayback() {
        if (!state.active) {
            stopSelfIfIdle()
            return
        }
        if (state.playing) return
        Log.i("$TAG 继续朗读")
        updateState(state.copy(playing = true))
        acquireWakeLock()
        acquireAudioFocus()
        if (paragraphs.isEmpty()) {
            // 正文还没加载完，重新加载当前章节；加载完会按 playing 状态继续
            if (state.mode == TtsPlaybackMode.BOOK && state.chapterIndex >= 0) {
                sessionJob?.cancel()
                sessionJob = scope.launch { playChapter(state.chapterIndex, state.paragraphIndex) }
            }
            return
        }
        startEngine(state.paragraphIndex)
    }

    /** 小说模式才有上一章/下一章，网页模式只朗读当前页 */
    fun nextChapter() = skipChapter(1)

    fun prevChapter() = skipChapter(-1)

    fun setSleepTimer(minutes: Int) {
        if (!state.active) {
            stopSelfIfIdle()
            return
        }
        timerJob?.cancel()
        timerJob = null
        sleepTimerMinutes = minutes
        if (minutes <= 0) {
            Log.i("$TAG 取消定时停止")
            updateState(state.copy(sleepTimerEndAt = 0L, sleepTimerMinutes = 0))
            return
        }
        Log.i("$TAG 定时停止：$minutes 分钟后")
        updateState(
            state.copy(
                sleepTimerEndAt = System.currentTimeMillis() + minutes * 60_000L,
                sleepTimerMinutes = minutes
            )
        )
        timerJob = scope.launch {
            var lastRemain = state.sleepTimerRemainingMinutes
            while (true) {
                delay(TIMER_TICK_MS)
                if (!state.active || state.sleepTimerEndAt <= 0L) break
                if (System.currentTimeMillis() >= state.sleepTimerEndAt) {
                    if (state.stopAfterChapter) {
                        // 到点但设置了「读完本章再停」：不再倒计时，等本章读完
                        Log.i("$TAG 定时时间到，等本章读完再停止")
                        stopPendingAfterChapter = true
                        updateState(
                            state.copy(
                                sleepTimerEndAt = 0L,
                                sleepTimerMinutes = 0,
                                stopPendingAfterChapter = true
                            )
                        )
                        toast("定时时间到，本章读完后停止朗读")
                        break
                    }
                    Log.i("$TAG 定时时间到，停止朗读")
                    toast("定时停止时间到，已停止朗读")
                    stopSession()
                    break
                }
                val remain = state.sleepTimerRemainingMinutes
                if (remain != lastRemain) {
                    lastRemain = remain
                    // 通知里展示剩余时间
                    notifyNotification()
                }
            }
        }
    }

    fun cycleSleepTimer() {
        if (!state.active) {
            stopSelfIfIdle()
            return
        }
        val next = nextSleepTimerMinutes()
        setSleepTimer(next)
        if (next <= 0) {
            toast("已取消定时停止")
        } else {
            toast("定时停止：$next 分钟后")
        }
    }

    /**
     * 设置「定时到点后读完当前章节再停止」（关掉时同时取消待停止状态）
     */
    fun setStopAfterChapter(enabled: Boolean) {
        if (!state.active) {
            stopSelfIfIdle()
            return
        }
        Log.i("$TAG 定时到点后读完本章再停：$enabled")
        stopAfterChapter = enabled
        if (!enabled) {
            stopPendingAfterChapter = false
        }
        updateState(state.copy(stopAfterChapter = enabled, stopPendingAfterChapter = stopPendingAfterChapter))
    }

    fun isPlayingNow(): Boolean = state.active && state.playing

    fun stopSession() {
        Log.i("$TAG 停止朗读")
        teardown()
        stopSelf()
    }

    // ==================== 朗读主流程（小说模式） ====================

    private suspend fun playChapter(index: Int, startParagraph: Int) {
        var chapterIndex = index
        var startPara = startParagraph
        var failed = 0
        while (true) {
            if (chapterIndex >= chapterList.size) {
                Log.i("$TAG 全书朗读完成")
                markBookEnd()
                stopSession()
                return
            }
            val chapter = chapterList.getOrNull(chapterIndex)
            if (chapter == null) {
                stopSession()
                return
            }
            // 不强制置为播放中：正文加载期间用户可能已经暂停
            updateState(
                state.copy(
                    chapterIndex = chapterIndex,
                    chapterUrl = chapter.url,
                    chapterTitle = chapter.title.orEmpty(),
                    paragraphIndex = startPara,
                    paragraphCount = 0,
                    paragraphText = "",
                    chapterProgressPercent = 0
                )
            )
            val list = if (chapterIndex == prefetchedIndex && prefetchedParagraphs.isNotEmpty()) {
                Log.i("$TAG 使用预加载的章节：${chapter.title}")
                prefetchedParagraphs.also { clearPrefetch() }
            } else {
                loadParagraphs(chapter)
            }
            if (list.isEmpty()) {
                failed++
                Log.e("$TAG 章节内容加载失败（第${chapterIndex + 1}章 ${chapter.title}）")
                if (failed >= MAX_FAILED_CHAPTER) {
                    toast("连续多章内容加载失败，已停止朗读")
                    stopSession()
                    return
                }
                chapterIndex++
                startPara = 0
                continue
            }
            failed = 0
            prepareParagraphs(list)
            val start = startPara.coerceIn(0, list.size - 1)
            updateState(state.copy(paragraphIndex = start, paragraphCount = list.size, paragraphText = list[start]))
            Log.i("$TAG 朗读第${chapterIndex + 1}章《${chapter.title}》共${list.size}段，从第${start + 1}段开始")
            // 阅读页不在前台时由服务写阅读进度；在前台时由阅读页自己写，避免互相覆盖
            saveReadingRecord(chapterIndex)
            if (state.playing) {
                acquireWakeLock()
                acquireAudioFocus()
                startEngine(start)
            } else {
                // 加载期间被暂停：内容已就绪，等用户继续
                Log.i("$TAG 第${chapterIndex + 1}章内容已就绪，当前处于暂停状态")
            }
            // 朗读本章期间预加载下一章，切章时无需等待
            prefetchNextChapter(chapterIndex + 1)
            return
        }
    }

    /** 预加载下一章正文（只入库，不朗读） */
    private fun prefetchNextChapter(nextIndex: Int) {
        prefetchJob?.cancel()
        prefetchJob = null
        clearPrefetch()
        if (state.mode != TtsPlaybackMode.BOOK) return
        val chapter = chapterList.getOrNull(nextIndex) ?: return
        val token = prefetchToken
        prefetchJob = scope.launch {
            Log.i("$TAG 预加载下一章《${chapter.title}》")
            val list = loadParagraphs(chapter)
            // 协程取消可能发生在最后一个挂起点之后，用 token 再确认一次，避免写入错章缓存
            if (list.isEmpty() || token != prefetchToken || state.mode != TtsPlaybackMode.BOOK) return@launch
            prefetchedIndex = nextIndex
            prefetchedParagraphs = list
            Log.i("$TAG 下一章预加载完成《${chapter.title}》共${list.size}段")
        }
    }

    private fun clearPrefetch() {
        prefetchToken++
        prefetchedIndex = -1
        prefetchedParagraphs = emptyList()
    }

    /**
     * 取章节正文并分段（数据库缓存优先，未入库时抓取入库；含 nextPageUrl 分页拼接）
     */
    private suspend fun loadParagraphs(chapter: Chapter): List<String> = withContext(Dispatchers.IO) {
        try {
            BookUseCase.getChapterContent(chapter, 0)
            if (chapter.content?.firstOrNull()?.contentError == true) {
                Log.e("$TAG 章节内容错误：${chapter.title}")
                return@withContext emptyList()
            }
            var page = 0
            while (page < MAX_PAGE_LOAD && BookUseCase.getChapterContentPage(chapter)) {
                page++
            }
            // 与阅读页一致：先把 HTML 转成纯文本（ChapterContent.format），再按页拼接
            val rawText = chapter.content
                ?.sortedBy { it.pageIndex }
                ?.joinToString(separator = "\n") { it.format() }
                .orEmpty()
            // 再做阅读页同一套文字处理：全角转换 + 简繁转换 + 净化规则
            val rules = ReplacementRuleUseCase.getEnabledRules()
            val jianFanMode = globalConfig.readerJianFanMode.value ?: ChapterTextPreparer.JIAN_FAN_OFF
            val fullWidthConvert = globalConfig.readerFullWidthConvert.value == true
            val title = ChapterTextPreparer.title(chapter.title, rules, book, chapter, jianFanMode, fullWidthConvert)
            val text = ChapterTextPreparer.content(rawText, rules, book, chapter, jianFanMode, fullWidthConvert)
            TtsChapterSplitter.split(title, text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e("$TAG 读取章节失败：${chapter.title}", e)
            emptyList()
        }
    }

    // ==================== 朗读引擎驱动 ====================

    /** 记录段落列表与朗读权重，并启动进度估算 */
    private fun prepareParagraphs(list: List<String>) {
        paragraphs = list
        paragraphWeights = buildWeights(list)
        totalWeight = paragraphWeights.sum()
        // 本章预估总时长，供播放器进度条使用
        updateState(state.copy(chapterEstimateMs = totalWeight * msPerWeight()))
        startProgressTicker()
    }

    private fun startEngine(startParagraph: Int) {
        if (paragraphs.isEmpty()) return
        val start = startParagraph.coerceIn(0, paragraphs.size - 1)
        // 从这里开始朗读：段内进度重新计时（暂停后继续，引擎也是从本段开头读起）
        resetParagraphProgressTracking()
        updateState(
            state.copy(
                paragraphIndex = start,
                paragraphText = paragraphs[start],
                paragraphStartAt = System.currentTimeMillis(),
                paragraphEstimateMs = estimateParagraphMs(start),
                paragraphProgress = 0f
            )
        )
        val generation = ++engineGeneration
        TtsService.paragraphChangeCallback = { index ->
            if (generation == engineGeneration) onParagraphChanged(index)
        }
        TtsService.segmentProgressCallback = { progress ->
            if (generation == engineGeneration) onEngineSegmentProgress(progress)
        }
        TtsService.onParagraphsCompleted = {
            if (generation == engineGeneration) onChapterCompleted()
        }
        sessionJob = scope.launch {
            // 启动前再确认代际号：这期间可能已经暂停/切章，不能把已停止的会话重新念起来
            if (generation != engineGeneration) {
                Log.i("$TAG 朗读会话已变化，放弃本次启动")
                return@launch
            }
            val started = TtsService.startParagraphs(paragraphs, start)
            if (!started && generation == engineGeneration && state.active && state.playing) {
                Log.e("$TAG 语音引擎启动失败")
                toast("朗读失败：语音引擎未就绪")
                stopSession()
            }
        }
    }

    private fun stopEngine() {
        engineGeneration++
        try {
            TtsService.stop()
        } catch (_: Throwable) {
        }
        TtsService.paragraphChangeCallback = null
        TtsService.segmentProgressCallback = null
        TtsService.onParagraphsCompleted = null
    }

    private fun onParagraphChanged(paragraphIndex: Int) {
        if (!state.active) return
        if (paragraphIndex == state.paragraphIndex && state.paragraphStartAt > 0L) {
            // 语音引擎在「本段开始」和「本段读完」时都会上报一次同一段落序号（进度估算需要），
            // 这里要忽略重复上报，否则段内进度会被重置、高亮闪回本段第一行
            return
        }
        if (paragraphIndex < state.paragraphIndex) {
            // 迟到的旧段落上报（引擎读完某段时会再上报一次该段），忽略，避免高亮回退
            return
        }
        val text = paragraphs.getOrNull(paragraphIndex).orEmpty()
        logSuspiciousParagraphHead(paragraphIndex, text)
        // 正常顺读时，上一段到本段的耗时就是上一段的真实朗读耗时，用来自校准进度模型；
        // 跳段（序号不连续）时耗时对不上，不参与校准
        if (paragraphIndex == state.paragraphIndex + 1 && state.paragraphStartAt > 0L) {
            calibrateMsPerWeight(
                paragraphWeights.getOrNull(state.paragraphIndex) ?: 0L,
                System.currentTimeMillis() - state.paragraphStartAt
            )
        }
        resetParagraphProgressTracking()
        updateState(
            state.copy(
                paragraphIndex = paragraphIndex,
                paragraphText = text,
                paragraphStartAt = System.currentTimeMillis(),
                paragraphEstimateMs = estimateParagraphMs(paragraphIndex),
                // 新段落从 0 开始
                paragraphProgress = 0f,
                playing = true
            )
        )
    }

    private fun onChapterCompleted() {
        if (!state.active) return
        if (state.mode == TtsPlaybackMode.PAGE) {
            // 网页模式只朗读当前页面，读完即停，不自动跳章
            Log.i("$TAG 网页正文朗读完成")
            stopSession()
            return
        }
        if (stopPendingAfterChapter) {
            // 定时已到点，等本章读完再停
            Log.i("$TAG 本章读完，按定时要求停止朗读")
            toast("本章读完，已停止朗读")
            stopSession()
            return
        }
        Log.i("$TAG 第${state.chapterIndex + 1}章朗读完成，进入下一章")
        sessionJob?.cancel()
        sessionJob = scope.launch { playChapter(state.chapterIndex + 1, 0) }
    }

    private fun skipChapter(delta: Int) {
        if (!state.active) {
            stopSelfIfIdle()
            return
        }
        if (state.mode != TtsPlaybackMode.BOOK) {
            Log.i("$TAG 网页模式不支持切换章节")
            return
        }
        if (chapterList.isEmpty()) return
        val target = (state.chapterIndex + delta).coerceIn(0, chapterList.size - 1)
        if (delta > 0 && target == state.chapterIndex) {
            Log.i("$TAG 已经是最后一章，结束朗读")
            stopSession()
            return
        }
        Log.i("$TAG 切换章节：${state.chapterIndex + 1} -> ${target + 1}")
        stopEngine()
        sessionJob?.cancel()
        sessionJob = scope.launch { playChapter(target, 0) }
    }

    // ==================== 预估进度 ====================

    /**
     * 按「章节段落权重 + 段落序号 + 段内进度」估算本章朗读进度。
     * 只回传整数百分比，避免高频刷新。
     */
    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (state.active) {
                delay(PROGRESS_TICK_MS)
                if (!state.active) break
                // 播放/暂停期间都要检测：系统媒体卡片在「播放中」被关闭时只会暂停会话并移除卡片，
                // 不会回调 onStop，这里靠卡片是否还在来判断播放器是否已被移除
                checkPlayerDismissed()
                if (!state.active) break
                if (!state.playing) continue
                // 兜底轮询：引擎能回传真实进度时主要由回调即时上报，这里负责没有回调的情况
                reportProgress(currentParagraphRatio())
            }
        }
    }

    /**
     * 播放控制面板（通知 / 媒体卡片）被移除时结束会话。
     *
     * 系统媒体卡片在「播放中」被关闭时只会暂停会话并把卡片移除，不会回调 onStop，
     * 于是会留下一个没有控制面板、状态还停在「暂停中」的会话，阅读器也会一直显示朗读中。
     * 这里检测自己的通知是否还存在：出现过、之后连续两次检测不到，就按「用户移除了播放器」处理，
     * 结束朗读（等价于停止），让阅读器状态恢复。
     *
     * 没有通知权限时通知从未出现过，不参与判断，避免误停机。
     */
    private fun checkPlayerDismissed() {
        if (!foregroundStarted || !state.active) return
        val posted = try {
            NotificationManagerCompat.from(this)
                .activeNotifications
                .any { it.id == TtsNotificationHelper.NOTIFICATION_ID }
        } catch (e: Throwable) {
            return
        }
        if (posted) {
            notificationSeen = true
            notificationMissingCount = 0
            return
        }
        if (!notificationSeen) return
        notificationMissingCount++
        if (notificationMissingCount >= MISSING_NOTIFICATION_LIMIT) {
            Log.i("$TAG 播放器已被移除，结束朗读会话")
            notificationMissingCount = 0
            notificationSeen = false
            stopSession()
        }
    }

    /** 换段/重新开始朗读时重置段内进度跟踪 */
    private fun resetParagraphProgressTracking() {
        engineSegmentProgress = -1f
        engineSegmentProgressAt = 0L
        paragraphRatioFloor = 0f
    }

    /**
     * 引擎回传的真实段内进度。
     * 系统 TTS 按「已朗读到的字符位置」回调，Edge 按「音频播放位置 / 音频总时长」回调。
     */
    private fun onEngineSegmentProgress(progress: Float) {
        if (!state.active || !state.playing) return
        val value = progress.coerceIn(0f, 1f)
        // 引擎按词/按帧回调，偶有乱序，同段内只接受前进的值；但仍刷新时间戳，
        // 因为引擎卡住（缓冲中）时进度本来就该停住，不该退回时间模型继续跑
        if (engineSegmentProgress < 0f || value >= engineSegmentProgress) {
            engineSegmentProgress = value
        }
        engineSegmentProgressAt = System.currentTimeMillis()
        // 引擎回调在 binder 线程，状态刷新切回主线程；
        // 这里立刻上报而不是等下一秒的估算轮询，否则高亮会明显落后于语音
        val now = engineSegmentProgressAt
        if (now - progressReportedAt < PROGRESS_REPORT_MIN_MS) return
        progressReportedAt = now
        scope.launch {
            if (state.active && state.playing) reportProgress(currentParagraphRatio())
        }
    }

    /**
     * 当前段内进度 0f~1f。
     *
     * 优先用引擎回传的真实朗读位置：比按时间估算准得多（不受引擎启动延迟、合成/缓冲卡顿影响）。
     * 引擎长时间没有回传真实位置（不支持范围回调、时长未知等）时，退回「段落开始时间 + 预估时长」的时间模型。
     */
    private fun currentParagraphRatio(): Float {
        val now = System.currentTimeMillis()
        val engineFresh = engineSegmentProgress >= 0f &&
            now - engineSegmentProgressAt <= ENGINE_PROGRESS_TIMEOUT_MS
        val ratio = if (engineFresh) {
            engineSegmentProgress
        } else {
            // 退回时间模型时不能低于已上报过的真实进度，否则高亮会往回跳
            maxOf(state.estimateParagraphRatio(), engineSegmentProgress.coerceAtLeast(0f))
        }
        // 同一段内进度只前进不回退（引擎乱序回调、两种模型交接时都可能出现短暂回退）
        if (ratio > paragraphRatioFloor) {
            paragraphRatioFloor = ratio
        }
        return paragraphRatioFloor
    }

    /**
     * 按整数百分比节流上报段内进度与章节进度。
     *
     * 段内进度只影响阅读页高亮，变化频繁；章节百分比变化才需要重建通知与播放器进度。
     */
    private fun reportProgress(ratio: Float) {
        val chapterPercent = estimateProgressPercent(state.paragraphIndex, ratio)
        val paragraphPercent = (ratio * 100).toInt().coerceIn(0, 100)
        if (chapterPercent == state.chapterProgressPercent &&
            paragraphPercent == (state.paragraphProgress * 100).toInt()
        ) {
            return
        }
        updateState(
            state.copy(
                chapterProgressPercent = chapterPercent,
                paragraphProgress = paragraphPercent / 100f
            ),
            refreshNotification = chapterPercent != state.chapterProgressPercent
        )
    }

    private fun estimateProgressPercent(paragraphIndex: Int, ratio: Float): Int {
        if (totalWeight <= 0L) return 0
        var done = 0L
        for (i in 0 until paragraphIndex.coerceAtMost(paragraphWeights.size)) {
            done += paragraphWeights[i]
        }
        if (paragraphIndex in paragraphWeights.indices) {
            done += (paragraphWeights[paragraphIndex] * ratio.coerceIn(0f, 1f)).toLong()
        }
        return ((done * 100) / totalWeight).toInt().coerceIn(0, 100)
    }

    private fun estimateParagraphMs(paragraphIndex: Int): Long {
        val weight = paragraphWeights.getOrNull(paragraphIndex) ?: return 0L
        return weight * msPerWeight()
    }

    /** 当前语速设置 */
    private fun currentTtsRate(): Int = globalConfig.readerTtsRate.value ?: 50

    /**
     * 每单位权重的朗读耗时（毫秒）。
     *
     * 同一语速下优先用实测值：固定模型（200ms/字）与实际语速偏差不小，
     * 用刚读完段落的真实耗时平滑后估算更准；换了语速或还没测到就退回固定模型。
     */
    private fun msPerWeight(): Long {
        val rate = currentTtsRate()
        if (measuredMsPerWeight > 0 && measuredMsPerWeightRate == rate) return measuredMsPerWeight
        var modelRate = rate
        if (modelRate > 50) {
            modelRate = 50 + (modelRate - 50) * 3
        }
        val speed = (modelRate / 50f).coerceAtLeast(0.2f)
        return (BASE_MS_PER_WEIGHT / speed).toLong().coerceAtLeast(20L)
    }

    /**
     * 用刚读完的段落校准「每权重耗时」。
     *
     * @param weight 刚读完段落的朗读权重
     * @param elapsedMs 该段从开始朗读到读完的耗时（同一语速设置下）
     */
    private fun calibrateMsPerWeight(weight: Long, elapsedMs: Long) {
        if (weight < MIN_CALIBRATE_WEIGHT || elapsedMs <= 0L) return
        val rate = currentTtsRate()
        val sample = (elapsedMs / weight).coerceIn(MIN_MS_PER_WEIGHT, MAX_MS_PER_WEIGHT)
        measuredMsPerWeight = if (measuredMsPerWeightRate == rate && measuredMsPerWeight > 0) {
            // 平滑一下，别被单段抖动带偏
            (measuredMsPerWeight * 3 + sample) / 4
        } else {
            sample
        }
        measuredMsPerWeightRate = rate
    }

    /** 段落朗读权重，与阅读页「按行定位」共用同一模型 */
    private fun buildWeights(paragraphs: List<String>): LongArray =
        LongArray(paragraphs.size) { TtsChapterSplitter.speakingWeight(paragraphs[it]) }

    /**
     * 诊断用：段落开头若含有标记/不可见字符，打一条日志说明送进语音引擎的是什么，
     * 便于定位「段落开头多出一个音」这类问题。正常段落不打印正文。
     */
    private fun logSuspiciousParagraphHead(paragraphIndex: Int, text: String) {
        val head = text.take(HEAD_CHECK_LENGTH)
        val suspicious = head.any {
            it == '<' || it == '>' || it == '&' ||
                    it == '＜' || it == '＞' || it == '＆' ||
                    it == '\uFEFF' || it == '\u200B' ||
                    it == '\u00AD' || (it.code < 0x20 && it != '\n')
        }
        if (suspicious) {
            Log.e("$TAG 第${paragraphIndex + 1}段开头疑似标记或不可见字符：[$head]")
        }
    }

    // ==================== 阅读进度 ====================

    private suspend fun saveReadingRecord(chapterIndex: Int) {
        if (state.mode != TtsPlaybackMode.BOOK) return
        if (TtsPlaybackController.isReaderVisible()) return
        val currentBook = book ?: return
        try {
            withContext(Dispatchers.IO) {
                val record = readingRecordDao.getReadingRecordSync(currentBook.title)
                    ?: ReadingRecord(currentBook.title, currentBook.id)
                record.bookId = currentBook.id
                record.chapterIndex = chapterIndex
                record.offest = 0
                record.scrollOffset = 0
                record.isEnd = false
                currentBook.cover?.takeIf { it.isNotBlank() }?.let { record.bookCover = it }
                currentBook.intro?.takeIf { it.isNotBlank() }?.let { record.bookIntro = it }
                record.updateTime = System.currentTimeMillis()
                readingRecordDao.insertReadingRecord(record)
            }
        } catch (e: Throwable) {
            Log.e("$TAG 保存阅读进度失败", e)
        }
    }

    private suspend fun markBookEnd() {
        val currentBook = book ?: return
        try {
            withContext(Dispatchers.IO) {
                val record = readingRecordDao.getReadingRecordSync(currentBook.title) ?: return@withContext
                record.isEnd = true
                record.updateTime = System.currentTimeMillis()
                readingRecordDao.insertReadingRecord(record)
            }
        } catch (e: Throwable) {
            Log.e("$TAG 标记读完失败", e)
        }
    }

    // ==================== 状态与通知 ====================

    private fun updateState(newState: TtsPlaybackState, refreshNotification: Boolean = true) {
        if (state == newState) return
        state = newState
        TtsPlaybackController.dispatchState(newState)
        mediaSession.update(newState, coverArtwork)
        if (refreshNotification) notifyNotification()
    }

    private fun startForegroundCompat(): Boolean {
        return try {
            ServiceCompat.startForeground(
                this,
                TtsNotificationHelper.NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
            foregroundStarted = true
            true
        } catch (e: Throwable) {
            foregroundStarted = false
            Log.e("$TAG 进入前台失败", e)
            false
        }
    }

    private fun notifyNotification() {
        if (!foregroundStarted) return
        try {
            NotificationManagerCompat.from(this)
                .notify(TtsNotificationHelper.NOTIFICATION_ID, buildNotification())
        } catch (e: Throwable) {
            Log.e("$TAG 更新通知失败", e)
        }
    }

    private fun buildNotification() = TtsNotificationHelper.build(this, state, mediaSession.token, coverArtwork)

    /**
     * 加载书籍封面，作为播放器（媒体卡片）左上角图标与通知大图。
     * 加载失败/没有封面时保持默认图标，不影响朗读。
     */
    private fun loadCoverArtwork(cover: String?) {
        coverJob?.cancel()
        coverJob = null
        coverArtwork = null
        if (cover.isNullOrBlank()) return
        coverJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                var target: FutureTarget<Bitmap>? = null
                try {
                    target = GlideApp.with(App.app)
                        .asBitmap()
                        .override(COVER_SIZE, COVER_SIZE)
                        .centerCrop()
                        .load(cover)
                        .submit()
                    val loaded = target.get()
                    // 复制一份：Glide 释放资源时不能把我们放进媒体元数据的位图回收掉
                    loaded?.copy(Bitmap.Config.ARGB_8888, false) ?: loaded
                } catch (e: Throwable) {
                    Log.e("$TAG 加载封面失败：$cover", e)
                    null
                } finally {
                    try {
                        target?.let { GlideApp.with(App.app).clear(it) }
                    } catch (_: Throwable) {
                    }
                }
            }
            if (bitmap == null || !state.active) return@launch
            coverArtwork = bitmap
            mediaSession.update(state, bitmap)
            notifyNotification()
        }
    }

    /** 定时停止按 关闭 -> 15 -> 30 -> 60 -> 关闭 循环 */
    private fun nextSleepTimerMinutes(): Int {
        if (sleepTimerMinutes <= 0) return SLEEP_TIMER_MINUTES.first()
        val nextIndex = SLEEP_TIMER_MINUTES.indexOf(sleepTimerMinutes) + 1
        return SLEEP_TIMER_MINUTES.getOrNull(nextIndex) ?: 0
    }

    /** 开始新会话前清空上一次会话的残留，避免念到上一本/上一页的内容 */
    /**
     * 打开阅读器的朗读菜单（卡片左侧按钮）：
     * 把阅读器带到前台并唤起设置菜单，菜单里就是播放卡片（播放控制、定时停止、朗读设置）
     */
    private fun openReaderMenu() {
        val bookId = state.bookId
        if (state.mode != TtsPlaybackMode.BOOK || bookId.isBlank()) {
            toast("当前为网页朗读，请在网页阅读页操作")
            return
        }
        try {
            startActivity(
                Intent(this, BookReaderActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(BOOK_ID, bookId)
                    .putExtra(SHOW_TTS_CARD, true)
            )
        } catch (e: Throwable) {
            Log.e("$TAG 打开阅读器朗读菜单失败", e)
            toast("请打开阅读器后使用朗读菜单")
        }
    }

    private fun resetSession() {
        sessionJob?.cancel()
        sessionJob = null
        timerJob?.cancel()
        timerJob = null
        progressJob?.cancel()
        progressJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        coverJob?.cancel()
        coverJob = null
        coverArtwork = null
        notificationSeen = false
        notificationMissingCount = 0
        sleepTimerMinutes = 0
        stopAfterChapter = false
        stopPendingAfterChapter = false
        stopEngine()
        paragraphs = emptyList()
        paragraphWeights = LongArray(0)
        totalWeight = 0L
        clearPrefetch()
        chapterList = emptyList()
        book = null
        pausedByFocus = false
    }

    private fun stopSelfIfIdle() {
        if (!state.active && !foregroundStarted) {
            Log.i("$TAG 当前没有朗读会话，结束服务")
            stopSelf()
        }
    }

    private fun teardown() {
        sessionJob?.cancel()
        sessionJob = null
        timerJob?.cancel()
        timerJob = null
        progressJob?.cancel()
        progressJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        coverJob?.cancel()
        coverJob = null
        coverArtwork = null
        notificationSeen = false
        notificationMissingCount = 0
        sleepTimerMinutes = 0
        stopAfterChapter = false
        stopPendingAfterChapter = false
        stopEngine()
        clearPrefetch()
        paragraphWeights = LongArray(0)
        totalWeight = 0L
        releaseWakeLock()
        releaseAudioFocus()
        foregroundStarted = false
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
        if (state != TtsPlaybackState()) {
            state = TtsPlaybackState()
            TtsPlaybackController.dispatchState(state)
        }
    }

    // ==================== 音频焦点与唤醒锁 ====================

    private fun acquireAudioFocus() {
        try {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setWillPauseWhenDucked(false)
                .build()
                .also { audioFocusRequest = it }
            val result = audioManager.requestAudioFocus(request)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.e("$TAG 音频焦点申请失败 result=$result")
            }
        } catch (e: Throwable) {
            Log.e("$TAG 音频焦点申请异常", e)
        }
    }

    private fun releaseAudioFocus() {
        try {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (_: Throwable) {
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = wakeLock ?: powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:tts"
            ).also { wakeLock = it }
            lock.setReferenceCounted(false)
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        } catch (e: Throwable) {
            Log.e("$TAG 申请唤醒锁失败", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {
        }
    }
}
