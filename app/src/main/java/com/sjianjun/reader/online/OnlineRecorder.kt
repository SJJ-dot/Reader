package com.sjianjun.reader.online

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.sjianjun.reader.tts.TtsPlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sjj.alog.Log

/**
 * 在线时长：本地按秒累计 + 批量上传，上传成功后清空本地记录。
 *
 * 计时口径和原来的心跳版一致——「App 在前台」或「后台听书（前台服务还在发声）」都算在线，
 * 区别只是不再每分钟上报一次：
 *
 * - 在线期间只把秒数写进本地账本 [OnlineStore]（按小时分桶：排行榜是滑动窗口，需要小时粒度）；
 * - 攒够了再整批上传，服务端确认后扣掉本地已上传的部分。
 *
 * 这样既省流量和电量，也顺带修掉了原来「按心跳间隔结算」的误差：停留不足一个心跳周期
 * 的访问一秒都记不上、切走 5 分钟以内再回来又会被整段算作在线。
 *
 * 上传时机（任一条命中就传，失败则保留、下次再传）：
 * 1. 启动时补传上次残留；
 * 2. 运行中每 [UPLOAD_INTERVAL_MS] 一次；
 * 3. 离开前台（最后一个 Activity 停止且不再朗读）时立即传。
 */
object OnlineRecorder {

    /** 运行中定时上传间隔 */
    private const val UPLOAD_INTERVAL_MS = 5 * 60_000L

    /** 把「已在线但还没落账」的时间写进本地账本的间隔：进程被杀最多丢这么多 */
    private const val PERSIST_INTERVAL_MS = 60_000L

    private val uploadLock = Mutex()

    private var job: Job? = null
    private var scope: CoroutineScope? = null

    private var startedActivities = 0

    /** 本轮「连续在线」的起点（elapsedRealtime）；为 null 表示当前不在线 */
    private var activeSince: Long? = null

    private var lastUploadAt = 0L

    fun start(scope: CoroutineScope, application: Application) {
        if (job != null) return
        this.scope = scope
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                syncActiveState()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                syncActiveState()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        // 听书由前台服务驱动：退到后台还在发声，这段时间同样算在线，所以跟着朗读状态同步
        TtsPlaybackController.state.observeForever { syncActiveState() }

        job = scope.launch {
            // 先把上次残留的记录补传上去
            upload()
            while (isActive) {
                delay(PERSIST_INTERVAL_MS)
                // 先落账一次，之后进程被杀最多只丢一个间隔
                persistActive()
                if (SystemClock.elapsedRealtime() - lastUploadAt >= UPLOAD_INTERVAL_MS) {
                    upload()
                }
            }
        }
    }

    /**
     * 立即落账并上传（[OnlineInfos.refresh] 用：顺带刷新在线人数和个人统计）。
     */
    suspend fun flushNow(): OnlineInfo? {
        persistActive()
        return upload(force = true)
    }

    private fun isOnline(): Boolean =
        startedActivities > 0 || TtsPlaybackController.isPlaying()

    /** 让记账状态与实际在线状态保持一致；从在线变为离线时立即上传 */
    @Synchronized
    private fun syncActiveState() {
        if (isOnline()) {
            if (activeSince == null) activeSince = SystemClock.elapsedRealtime()
            return
        }
        val since = activeSince ?: return
        activeSince = null
        OnlineStore.add((SystemClock.elapsedRealtime() - since) / 1000)
        scope?.launch { upload() }
    }

    /** 把本轮已在线的时间写进本地账本（不改变在线状态） */
    @Synchronized
    private fun persistActive() {
        val since = activeSince ?: return
        val now = SystemClock.elapsedRealtime()
        activeSince = now
        OnlineStore.add((now - since) / 1000)
    }

    /**
     * 上传本地账本，成功后扣掉已上传部分并刷新 [OnlineInfos.onlineInfo]。
     *
     * @param force 为真时即使本地没有记录也发一次，用于刷新在线人数
     */
    private suspend fun upload(force: Boolean = false): OnlineInfo? = uploadLock.withLock {
        val items = OnlineStore.snapshot()
        if (items.isEmpty() && !force) return@withLock null
        lastUploadAt = SystemClock.elapsedRealtime()
        val online = try {
            OnlineApi.report(onlineDeviceId, items)
        } catch (e: Exception) {
            Log.e("在线时长上报失败: ${e.message}")
            null
        } ?: return@withLock null
        OnlineStore.subtract(items)
        OnlineInfos.onlineInfo.postValue(online)
        online
    }
}
