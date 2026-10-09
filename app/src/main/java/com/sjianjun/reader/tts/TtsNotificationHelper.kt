package com.sjianjun.reader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.sjianjun.reader.BOOK_ID
import com.sjianjun.reader.R
import com.sjianjun.reader.URL
import com.sjianjun.reader.module.bookcity.BrowserReadModeActivity
import com.sjianjun.reader.module.main.MainActivity
import com.sjianjun.reader.module.reader.activity.BookReaderActivity

/**
 * 朗读通知：渠道创建 + MediaStyle 通知构建。
 *
 * 通知展示书名/页面标题（主标题）与章节名/进度（副标题），提供播放/暂停、停止朗读、
 * 定时停止；小说模式额外提供上一章、下一章（网页模式只朗读当前页，不提供切章）。
 */
object TtsNotificationHelper {

    const val CHANNEL_ID = "reader_tts_playback"
    const val NOTIFICATION_ID = 1010

    private const val CHANNEL_NAME = "朗读播放控制"

    private const val REQUEST_CODE_CONTENT = 100
    private const val REQUEST_CODE_PREV = 101
    private const val REQUEST_CODE_TOGGLE = 102
    private const val REQUEST_CODE_NEXT = 103
    private const val REQUEST_CODE_STOP = 104
    private const val REQUEST_CODE_TIMER = 105

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        channel.description = "朗读播放控制"
        channel.setShowBadge(false)
        channel.enableVibration(false)
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        state: TtsPlaybackState,
        token: MediaSessionCompat.Token,
        artwork: Bitmap? = null,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts_notification)
            .setContentTitle(state.bookTitle.ifBlank { CHANNEL_NAME })
            .setContentText(buildContentText(state))
            .setContentIntent(contentPendingIntent(context, state))
            .setDeleteIntent(servicePendingIntent(context, TtsPlaybackService.ACTION_STOP, REQUEST_CODE_STOP))
            // 暂停时允许用户划掉通知/播放器；划掉即结束朗读会话（不会留下一个悬着的暂停会话）
            .setOngoing(state.playing)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        artwork?.let { builder.setLargeIcon(it) }

        // 折叠态只显示 3 个按钮；小说模式是 上一章/暂停/下一章，网页模式是 暂停/停止/定时
        val actionCount = addActions(context, builder, state)
        if (state.paragraphCount > 0) {
            // 预估朗读进度（章节 + 段落 + 时间估算）
            builder.setProgress(100, state.chapterProgressPercent, false)
        }

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(token)
        if (actionCount >= 3) {
            style.setShowActionsInCompactView(0, 1, 2)
        }
        builder.setStyle(style)
        return builder.build()
    }

    private fun addActions(
        context: Context,
        builder: NotificationCompat.Builder,
        state: TtsPlaybackState
    ): Int {
        var count = 0
        if (state.mode == TtsPlaybackMode.BOOK) {
            builder.addAction(
                R.drawable.ic_tts_prev,
                "上一章",
                servicePendingIntent(context, TtsPlaybackService.ACTION_PREV, REQUEST_CODE_PREV)
            )
            count++
        }
        builder.addAction(
            if (state.playing) R.drawable.ic_tts_pause else R.drawable.ic_tts_play,
            if (state.playing) "暂停" else "播放",
            servicePendingIntent(context, TtsPlaybackService.ACTION_TOGGLE, REQUEST_CODE_TOGGLE)
        )
        count++
        if (state.mode == TtsPlaybackMode.BOOK) {
            builder.addAction(
                R.drawable.ic_tts_next,
                "下一章",
                servicePendingIntent(context, TtsPlaybackService.ACTION_NEXT, REQUEST_CODE_NEXT)
            )
            count++
        }
        builder.addAction(
            R.drawable.ic_tts_stop,
            "停止",
            servicePendingIntent(context, TtsPlaybackService.ACTION_STOP, REQUEST_CODE_STOP + 10)
        )
        count++
        builder.addAction(
            R.drawable.ic_tts_timer,
            sleepTimerActionTitle(state.sleepTimerMinutes),
            servicePendingIntent(context, TtsPlaybackService.ACTION_CYCLE_TIMER, REQUEST_CODE_TIMER)
        )
        count++
        return count
    }

    private fun buildContentText(state: TtsPlaybackState): String {
        val parts = ArrayList<String>(3)
        if (state.mode == TtsPlaybackMode.PAGE) {
            parts.add("网页阅读")
        } else {
            parts.add(
                state.chapterTitle.ifBlank {
                    if (state.chapterIndex >= 0) "第${state.chapterIndex + 1}章" else ""
                }
            )
        }
        if (state.paragraphCount > 0) {
            parts.add("${state.chapterProgressPercent}%")
        }
        val remain = state.sleepTimerRemainingMinutes
        if (remain > 0) {
            parts.add("$remain 分钟后停止")
        }
        return parts.filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun contentPendingIntent(context: Context, state: TtsPlaybackState): PendingIntent {
        val intent = when {
            state.mode == TtsPlaybackMode.PAGE && state.pageUrl.isNotBlank() -> {
                // 网页模式回到正在朗读的页面
                Intent(context, BrowserReadModeActivity::class.java)
                    .putExtra(URL, state.pageUrl)
            }

            state.bookId.isNotBlank() -> {
                // 小说模式回到正在朗读的书籍与章节
                Intent(context, BookReaderActivity::class.java).putExtra(BOOK_ID, state.bookId)
            }

            else -> Intent(context, MainActivity::class.java)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, TtsPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
