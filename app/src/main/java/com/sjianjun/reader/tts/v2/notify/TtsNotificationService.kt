package com.sjianjun.reader.tts.v2.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sjianjun.reader.App
import com.sjianjun.reader.BOOK_ID
import com.sjianjun.reader.GlideApp
import com.sjianjun.reader.R
import com.sjianjun.reader.SHOW_TTS_CARD
import com.sjianjun.reader.URL
import com.sjianjun.reader.module.bookcity.BrowserReadModeActivity
import com.sjianjun.reader.module.main.MainActivity
import com.sjianjun.reader.module.reader.activity.BookReaderActivity
import com.sjianjun.reader.repository.DbFactory
import com.sjianjun.reader.tts.v2.TtsPlayerHolder
import com.sjianjun.reader.tts.v2.session.TtsPlayer
import com.sjianjun.reader.tts.v2.session.TtsSessionMode
import com.sjianjun.reader.tts.v2.session.TtsSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 通知层：独立的朗读前台服务，只做「展示 + 控制」。
 *
 * - 展示：通知（播放/暂停、上一章/下一章、停止、定时）、媒体卡片（MediaSession）
 * - 控制：通知按钮、锁屏/耳机/车机媒体按键 → 转发给会话层 [TtsPlayer]
 * - 平台事务：前台服务、音频焦点、WakeLock、耳机拔出、播放器（通知）被移除检测
 *
 * 不持有任何会话逻辑：状态只从 `TtsPlayerHolder.player().state` 读，命令只往里发。
 */
class TtsNotificationService : Service() {

    companion object {
        private const val TAG = "TtsNotifyService"

        const val ACTION_START = "com.sjianjun.reader.tts.v2.START"
        const val ACTION_TOGGLE = "com.sjianjun.reader.tts.v2.TOGGLE"
        const val ACTION_PAUSE = "com.sjianjun.reader.tts.v2.PAUSE"
        const val ACTION_RESUME = "com.sjianjun.reader.tts.v2.RESUME"
        const val ACTION_STOP = "com.sjianjun.reader.tts.v2.STOP"
        const val ACTION_PREV = "com.sjianjun.reader.tts.v2.PREV"
        const val ACTION_NEXT = "com.sjianjun.reader.tts.v2.NEXT"
        const val ACTION_CYCLE_TIMER = "com.sjianjun.reader.tts.v2.CYCLE_TIMER"
        const val ACTION_OPEN_READER = "com.sjianjun.reader.tts.v2.OPEN_READER"
        const val ACTION_OPEN_SETTING = "com.sjianjun.reader.tts.v2.OPEN_SETTING"

        /** 媒体卡片左侧自定义按钮：打开朗读设置（与通知「设置」一致） */
        private const val ACTION_CARD_SETTING = "com.sjianjun.reader.tts.v2.CARD_SETTING"

        /** 媒体卡片右侧自定义按钮：停止朗读 */
        private const val ACTION_CARD_STOP = "com.sjianjun.reader.tts.v2.CARD_STOP"

        const val CHANNEL_ID = "reader_tts_v2_playback"
        const val NOTIFICATION_ID = 1011

        private const val CHANNEL_NAME = "朗读播放控制"

        /** 定时停止可选时长（分钟），按顺序切换：关闭 -> 15 -> 30 -> 60 -> 关闭 */
        private val SLEEP_TIMER_MINUTES = intArrayOf(15, 30, 60)

        /** 检测"播放器（通知）被移除"的轮询间隔与阈值 */
        private const val DISMISS_POLL_MS = 1_000L
        private const val DISMISS_LIMIT = 3

        private const val WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1000L

        /** 封面尺寸：跨进程拷贝到通知/媒体卡片，不能太大 */
        private const val COVER_SIZE = 192

        /** 测试用：最近一次同步给系统的播放状态（时长/位置/自定义按钮），用于断言时间轴是否在走 */
        @Volatile
        internal var lastPlaybackSnapshot: PlaybackSnapshot? = null

        internal data class PlaybackSnapshot(
            val durationMs: Long,
            val positionMs: Long,
            val playing: Boolean,
            val chapterTitle: String,
            val customActions: List<String>,
        )

        private const val REQUEST_CODE_CONTENT = 200
        private const val REQUEST_CODE_PREV = 201
        private const val REQUEST_CODE_TOGGLE = 202
        private const val REQUEST_CODE_NEXT = 203
        private const val REQUEST_CODE_STOP = 204
        private const val REQUEST_CODE_TIMER = 205
        private const val REQUEST_CODE_SETTING = 206

        /** 启动通知服务（会话开始后由交互层调用） */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TtsNotificationService::class.java).setAction(ACTION_START)
                )
            } catch (e: Throwable) {
                Log.e("$TAG 启动通知服务失败", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, TtsNotificationService::class.java))
            } catch (e: Throwable) {
                Log.e("$TAG 停止通知服务失败", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var mediaSession: MediaSessionCompat
    private var stateJob: Job? = null
    private var pollJob: Job? = null

    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted = false
    private var pausedByFocus = false

    /** 通知是否出现过：出现过再消失才算"用户移除了播放器"（没有通知权限时永不出现，不能误停） */
    private var notificationSeen = false
    private var missingCount = 0

    /** 书籍封面（媒体卡片/通知大图）；按书籍变化懒加载 */
    private var coverArtwork: android.graphics.Bitmap? = null
    private var coverBookId = ""

    /** 媒体会话的节流键：进度每 100ms 刷新，播放状态/元数据不必跟着刷新 */
    private var lastPlaybackKey: String? = null
    private var lastMetadataKey: String? = null

    /** 通知的节流键：内容没变就不重复 notify */
    private var lastNotificationKey: String? = null

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.i("$TAG 耳机拔出，暂停朗读")
                player().pause()
            }
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val current = player().state.value
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.i("$TAG 音频焦点丢失，停止朗读")
                player().stop()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedByFocus = current.playing
                Log.i("$TAG 音频焦点被临时占用，暂停朗读 pausedByFocus=$pausedByFocus")
                player().pause()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByFocus) {
                    pausedByFocus = false
                    Log.i("$TAG 音频焦点恢复，继续朗读")
                    player().resume()
                }
            }
        }
    }

    private fun player(): TtsPlayer = TtsPlayerHolder.player()

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        mediaSession = MediaSessionCompat(this, "TtsV2MediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = player().resume()
                override fun onPause() = player().pause()
                override fun onStop() = player().stop()
                override fun onSkipToNext() = player().nextChapter()
                override fun onSkipToPrevious() = player().prevChapter()

                // 媒体卡片上的两个自定义按钮（左设置、右停止）
                override fun onCustomAction(action: String?, extras: Bundle?) {
                    when (action) {
                        ACTION_CARD_SETTING -> openTtsSetting()
                        ACTION_CARD_STOP -> player().stop()
                    }
                }
            })
            isActive = true
        }
        try {
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        } catch (e: Throwable) {
            Log.e("$TAG 注册耳机拔出监听失败", e)
        }
        // 先进入前台，避免 startForegroundService 后 5 秒内没 startForeground 被杀
        startForegroundCompat(player().state.value)
        // 订阅 holder 的稳定状态入口：引擎切换重建播放器后仍能继续更新通知
        stateJob = scope.launch {
            TtsPlayerHolder.state.collect { state -> onStateChanged(state) }
        }
        startDismissPoll()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                val p = player()
                if (p.state.value.playing) p.pause() else p.resume()
            }

            ACTION_PAUSE -> player().pause()
            ACTION_RESUME -> player().resume()
            ACTION_STOP -> player().stop()
            ACTION_PREV -> player().prevChapter()
            ACTION_NEXT -> player().nextChapter()
            ACTION_CYCLE_TIMER -> cycleSleepTimer()
            ACTION_OPEN_READER -> openReader()
            ACTION_OPEN_SETTING -> openTtsSetting()
            ACTION_START, null -> Unit
            else -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisyReceiver) }
        stateJob?.cancel()
        pollJob?.cancel()
        releaseWakeLock()
        releaseAudioFocus()
        runCatching { mediaSession.isActive = false; mediaSession.release() }
        scope.cancel()
        super.onDestroy()
    }

    // ==================== 状态 → 通知/媒体卡片 ====================

    private fun onStateChanged(state: TtsSessionState) {
        if (!state.active) {
            Log.i("$TAG 会话已结束，退出前台并停止服务")
            stopForegroundCompat()
            stopSelf()
            return
        }
        if (!foregroundStarted) {
            startForegroundCompat(state)
        } else {
            notify(state)
        }
        updateMediaSession(state)
        // 书籍变化时加载封面（媒体卡片左上角图标/通知大图）
        if (state.mode == TtsSessionMode.BOOK && state.bookId.isNotBlank() && state.bookId != coverBookId) {
            loadCoverArtwork(state.bookId)
        }
        if (state.playing) {
            acquireAudioFocus()
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun updateMediaSession(state: TtsSessionState) {
        // 时长/位置来自会话层的章节时间轴估算：媒体卡片（HyperOS 等）的时间与进度条就靠它
        val duration = state.chapterDurationMs
        val position = if (duration > 0L) {
            state.chapterPositionMs.coerceIn(0L, duration)
        } else {
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
        }
        // 供自动化断言：时间轴必须有真实时长与位置，卡片自定义按钮要在
        lastPlaybackSnapshot = PlaybackSnapshot(
            durationMs = duration,
            positionMs = position,
            playing = state.playing,
            chapterTitle = state.chapterTitle,
            customActions = listOf("朗读设置", "停止朗读"),
        )
        // 进度每 100ms 刷新一次，播放状态按秒节流即可（系统会按 speed 自行推进）
        val playbackKey = "${state.playing}|${state.chapterIndex}|${state.chapterTitle}|$duration|${position / 1000}"
        if (playbackKey != lastPlaybackKey) {
            lastPlaybackKey = playbackKey
            var actions = PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
            if (state.mode == TtsSessionMode.BOOK) {
                actions = actions or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            }
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(actions)
                // 卡片上的自定义按钮：左「设置」右「停止」（系统按顺序填充）
                .addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        ACTION_CARD_SETTING, "朗读设置", R.drawable.ic_tts_settings
                    ).build()
                )
                .addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        ACTION_CARD_STOP, "停止朗读", R.drawable.ic_tts_stop
                    ).build()
                )
                .setState(
                    if (state.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    position,
                    if (state.playing) 1f else 0f
                )
                .build()
            mediaSession.setPlaybackState(playbackState)
        }

        // 元数据几乎不变：只在书名/章节/时长/封面变化时更新（封面位图跨进程拷贝开销大）
        val metadataKey =
            "${state.bookId}|${state.bookTitle}|${state.chapterTitle}|$duration|${coverArtwork?.let { System.identityHashCode(it) }}"
        if (metadataKey == lastMetadataKey) return
        lastMetadataKey = metadataKey
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.bookTitle.ifBlank { state.chapterTitle })
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, state.bookTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.chapterTitle)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (duration > 0L) duration else 0L)
        coverArtwork?.let { bitmap ->
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
        }
        mediaSession.setMetadata(metadata.build())
    }

    // ==================== 前台/通知 ====================

    private fun startForegroundCompat(state: TtsSessionState) {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(state),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
            foregroundStarted = true
        } catch (e: Throwable) {
            foregroundStarted = false
            Log.e("$TAG 进入前台失败", e)
        }
    }

    private fun stopForegroundCompat() {
        foregroundStarted = false
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
    }

    private fun notify(state: TtsSessionState) {
        if (!foregroundStarted) return
        // 进度每 100ms 刷新一次，通知内容没变就不重复提交（否则每个 tick 都跨进程更新一次）
        val key = "${state.playing}|${state.bookId}|${state.bookTitle}|${state.chapterIndex}|" +
                "${state.chapterTitle}|${state.chapterProgressPercent}|${state.sleepTimerMinutes}|${state.stopAfterChapter}"
        if (key == lastNotificationKey) return
        lastNotificationKey = key
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(state))
        } catch (e: Throwable) {
            Log.e("$TAG 更新通知失败", e)
        }
    }

    private fun buildNotification(state: TtsSessionState): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts_notification)
            .setContentTitle(state.bookTitle.ifBlank { CHANNEL_NAME })
            .setContentText(buildContentText(state))
            .setContentIntent(contentPendingIntent(state))
            .setDeleteIntent(servicePendingIntent(ACTION_STOP, REQUEST_CODE_STOP))
            // 暂停时允许划掉通知（划掉即结束会话）；播放中保持常驻
            .setOngoing(state.playing)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // 封面作为媒体卡片/通知大图
        coverArtwork?.let { builder.setLargeIcon(it) }

        // 折叠态只显示前 3 个动作，所以「停止」必须在前 3 个里，否则用户看不到
        // 小说：[播放/暂停] [下一章] [停止] [定时] [设置]
        // 网页：[播放/暂停] [停止] [定时] [设置]
        builder.addAction(
            if (state.playing) R.drawable.ic_tts_pause else R.drawable.ic_tts_play,
            if (state.playing) "暂停" else "播放",
            servicePendingIntent(ACTION_TOGGLE, REQUEST_CODE_TOGGLE)
        )
        if (state.mode == TtsSessionMode.BOOK) {
            builder.addAction(R.drawable.ic_tts_next, "下一章", servicePendingIntent(ACTION_NEXT, REQUEST_CODE_NEXT))
        }
        builder.addAction(R.drawable.ic_tts_stop, "停止", servicePendingIntent(ACTION_STOP, REQUEST_CODE_STOP + 10))
        builder.addAction(
            R.drawable.ic_tts_timer,
            sleepTimerActionTitle(state.sleepTimerMinutes),
            servicePendingIntent(ACTION_CYCLE_TIMER, REQUEST_CODE_TIMER)
        )
        builder.addAction(
            R.drawable.ic_tts_settings,
            "设置",
            servicePendingIntent(ACTION_OPEN_SETTING, REQUEST_CODE_SETTING)
        )

        if (state.paragraphCount > 0) {
            builder.setProgress(100, state.chapterProgressPercent, false)
        }
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
        style.setShowActionsInCompactView(0, 1, 2)
        builder.setStyle(style)
        return builder.build()
    }

    private fun buildContentText(state: TtsSessionState): String {
        val parts = ArrayList<String>(3)
        parts.add(
            if (state.mode == TtsSessionMode.PAGE) {
                "网页阅读"
            } else {
                state.chapterTitle.ifBlank {
                    if (state.chapterIndex >= 0) "第${state.chapterIndex + 1}章" else ""
                }
            }
        )
        if (state.paragraphCount > 0) {
            parts.add("${state.chapterProgressPercent}%")
        }
        val remain = sleepTimerRemainingMinutes(state)
        if (remain > 0) {
            parts.add("$remain 分钟后停止")
        }
        return parts.filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun sleepTimerActionTitle(minutes: Int): String = when (minutes) {
        15 -> "定时30分钟"
        30 -> "定时60分钟"
        60 -> "取消定时"
        else -> "定时停止"
    }

    private fun sleepTimerRemainingMinutes(state: TtsSessionState): Int {
        if (state.sleepTimerEndAt <= 0L) return 0
        val remain = state.sleepTimerEndAt - System.currentTimeMillis()
        return if (remain <= 0L) 0 else ((remain + 59_999L) / 60_000L).toInt()
    }

    private fun contentPendingIntent(state: TtsSessionState): PendingIntent {
        val intent = if (state.mode == TtsSessionMode.BOOK && state.bookId.isNotBlank()) {
            Intent(this, BookReaderActivity::class.java).putExtra(BOOK_ID, state.bookId)
        } else {
            Intent(this, MainActivity::class.java)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, REQUEST_CODE_CONTENT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TtsNotificationService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ==================== 定时/切章/打开阅读器 ====================

    private fun cycleSleepTimer() {
        val current = player().state.value.sleepTimerMinutes
        val next = nextSleepTimerMinutes(current)
        player().setSleepTimer(next)
    }

    private fun nextSleepTimerMinutes(minutes: Int): Int {
        if (minutes <= 0) return SLEEP_TIMER_MINUTES.first()
        val nextIndex = SLEEP_TIMER_MINUTES.indexOf(minutes) + 1
        return SLEEP_TIMER_MINUTES.getOrNull(nextIndex) ?: 0
    }

    private fun openReader() {
        val state = player().state.value
        val intent = if (state.mode == TtsSessionMode.BOOK && state.bookId.isNotBlank()) {
            Intent(this, BookReaderActivity::class.java)
                .putExtra(BOOK_ID, state.bookId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } else {
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(intent) }.onFailure { Log.e("$TAG 打开阅读器失败", it) }
    }

    /** 加载书籍封面（失败保持默认图标，不影响朗读） */
    private fun loadCoverArtwork(bookId: String) {
        coverBookId = bookId
        coverArtwork = null
        scope.launch {
            val cover = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { DbFactory.db.bookDao().getBookById(bookId)?.cover }.getOrNull()
            }
            if (cover.isNullOrBlank()) return@launch
            val bitmap = kotlinx.coroutines.withContext(Dispatchers.IO) {
                var target: com.bumptech.glide.request.FutureTarget<android.graphics.Bitmap>? = null
                try {
                    target = GlideApp.with(applicationContext)
                        .asBitmap()
                        .override(COVER_SIZE, COVER_SIZE)
                        .centerCrop()
                        .load(cover)
                        .submit()
                    // 复制一份：Glide 释放资源时不能把放进通知/媒体元数据的位图回收掉
                    target.get()?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                } catch (e: Throwable) {
                    Log.e("$TAG 加载封面失败：$cover", e)
                    null
                } finally {
                    runCatching { target?.let { GlideApp.with(applicationContext).clear(it) } }
                }
            }
            if (bitmap == null) return@launch
            coverArtwork = bitmap
            // 封面变了要强制重绘一次（内容节流键不含封面）
            lastNotificationKey = null
            lastMetadataKey = null
            updateMediaSession(player().state.value)
            notify(player().state.value)
        }
    }

    /**
     * 通知「设置」：打开正在朗读的内容，并直接唤起朗读播放卡片（里面可调语速/定时）。
     */
    private fun openTtsSetting() {
        val state = player().state.value
        val intent = when {
            state.mode == TtsSessionMode.BOOK && state.bookId.isNotBlank() -> {
                Intent(this, BookReaderActivity::class.java)
                    .putExtra(BOOK_ID, state.bookId)
                    .putExtra(SHOW_TTS_CARD, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            state.mode == TtsSessionMode.PAGE && state.pageUrl.isNotBlank() -> {
                Intent(this, BrowserReadModeActivity::class.java)
                    .putExtra(URL, state.pageUrl)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            else -> {
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
        runCatching { startActivity(intent) }.onFailure { Log.e("$TAG 打开朗读设置失败", it) }
    }

    // ==================== 播放器被移除检测 ====================

    private fun startDismissPoll() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(DISMISS_POLL_MS)
                val state = player().state.value
                if (!state.active || !foregroundStarted) {
                    missingCount = 0
                    continue
                }
                val posted = try {
                    NotificationManagerCompat.from(this@TtsNotificationService)
                        .activeNotifications
                        .any { it.id == NOTIFICATION_ID }
                } catch (e: Throwable) {
                    continue
                }
                if (posted) {
                    notificationSeen = true
                    missingCount = 0
                    continue
                }
                // 没有通知权限时通知从未出现过，不参与判断，避免误停
                if (!notificationSeen) continue
                missingCount++
                if (missingCount >= DISMISS_LIMIT) {
                    Log.i("$TAG 播放器已被移除，结束朗读会话")
                    missingCount = 0
                    notificationSeen = false
                    player().stop()
                }
            }
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
                "$packageName:tts-v2"
            ).also { wakeLock = it }
            lock.setReferenceCounted(false)
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        } catch (e: Throwable) {
            Log.e("$TAG 申请唤醒锁失败", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        channel.description = "朗读播放控制"
        channel.setShowBadge(false)
        channel.enableVibration(false)
        manager.createNotificationChannel(channel)
    }
}
