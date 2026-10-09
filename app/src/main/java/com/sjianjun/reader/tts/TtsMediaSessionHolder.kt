package com.sjianjun.reader.tts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.sjianjun.reader.R

/**
 * MediaSessionCompat 封装。
 *
 * 让朗读以「音乐播放器」的方式出现在通知栏、锁屏、Android 13+ 系统媒体控制卡片上，
 * 并接收耳机 / 蓝牙 / 车机按键（播放暂停、上一首、下一首）。
 */
class TtsMediaSessionHolder(context: Context, private val callback: Callback) {

    interface Callback {
        fun onPlay()
        fun onPause()
        fun onStop()
        fun onPrevChapter()
        fun onNextChapter()

        /** 打开阅读器的朗读菜单（卡片左侧按钮） */
        fun onReaderMenu()
    }

    private val appContext = context.applicationContext
    private val session = MediaSessionCompat(appContext, "ReaderTtsPlayback")

    /** 上一次同步给系统的元数据标识，避免进度刷新时重复拷贝封面位图 */
    private var lastMetadataKey: String? = null

    val token: MediaSessionCompat.Token get() = session.sessionToken

    init {
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                callback.onPlay()
            }

            override fun onPause() {
                callback.onPause()
            }

            override fun onStop() {
                callback.onStop()
            }

            override fun onSkipToNext() {
                callback.onNextChapter()
            }

            override fun onSkipToPrevious() {
                callback.onPrevChapter()
            }

            override fun onCustomAction(action: String?, extras: Bundle?) {
                when (action) {
                    ACTION_READER_MENU -> callback.onReaderMenu()
                    ACTION_STOP_PLAYBACK -> callback.onStop()
                }
            }
        })
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        // 耳机/蓝牙按键在会话不支持时仍需兜底：把媒体按键直接转成开关命令给服务
        session.setMediaButtonReceiver(
            PendingIntent.getService(
                appContext,
                REQUEST_CODE_MEDIA_BUTTON,
                Intent(appContext, TtsPlaybackService::class.java)
                    .setAction(TtsPlaybackService.ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        session.isActive = true
    }

    /**
     * 同步当前朗读状态、书籍信息、封面与预估进度
     *
     * @param artwork 书籍封面位图（可为空），会作为媒体卡片左上角的图标
     */
    fun update(state: TtsPlaybackState, artwork: Bitmap? = null) {
        // 网页模式只朗读当前页，不提供上一章/下一章
        var actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP
        if (state.mode == TtsPlaybackMode.BOOK) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }
        // 播放器上只放「停止朗读」自定义按钮（定时停止只保留在通知栏按钮里）
        val duration = state.chapterEstimateMs
        val position = if (duration > 0L) {
            duration * state.chapterProgressPercent.coerceIn(0, 100) / 100
        } else {
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
        }
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            // 卡片上自定义按钮的位置由系统按顺序填充：第一个在左、第二个在右。
            // 左侧放「朗读菜单」（打开阅读器里的播放卡片菜单），右侧放「停止朗读」
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_READER_MENU,
                    "朗读菜单",
                    R.drawable.ic_tts_settings
                ).build()
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_STOP_PLAYBACK,
                    "停止朗读",
                    R.drawable.ic_tts_stop
                ).build()
            )
            .setState(
                if (state.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                position,
                if (state.playing) 1f else 0f
            )
            .build()
        session.setPlaybackState(playbackState)

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.bookTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, state.bookTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.chapterTitle)
        if (duration > 0L) {
            metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        }
        artwork?.let { bitmap ->
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
        }
        // 进度会频繁刷新，但元数据（书名/章节名/封面）几乎不变：
        // 封面位图每次 setMetadata 都要跨进程拷贝一遍，所以只在真正变化时才更新
        val metadataKey = "${state.bookTitle}|${state.chapterTitle}|$duration|${System.identityHashCode(artwork)}"
        if (metadataKey != lastMetadataKey) {
            lastMetadataKey = metadataKey
            session.setMetadata(metadata.build())
        }
    }

    fun release() {
        try {
            session.setActive(false)
        } catch (_: Throwable) {
        }
        try {
            session.release()
        } catch (_: Throwable) {
        }
    }

    companion object {
        /** 卡片左侧：打开阅读器的朗读菜单 */
        const val ACTION_READER_MENU = "com.sjianjun.reader.tts.READER_MENU"

        /** 卡片右侧：停止朗读 */
        const val ACTION_STOP_PLAYBACK = "com.sjianjun.reader.tts.STOP_PLAYBACK"
        private const val REQUEST_CODE_MEDIA_BUTTON = 200
    }
}
