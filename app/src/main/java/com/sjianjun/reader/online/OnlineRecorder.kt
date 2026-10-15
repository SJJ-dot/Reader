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
 * 计时口径：「App 在前台且有焦点」或「后台听书（前台服务还在发声）」算在线。
 * 息屏、切走、分屏失焦都会让页面进入 paused，因此统一用 onPause 作为「离开」信号。
 *
 * **上传时机只有三个**：
 * 1. 启动时立即报活一次（并把上次残留的记录一起补传）；
 * 2. 运行中每 [UPLOAD_INTERVAL_MS] 一次（本地没数据就跳过，不发请求）；
 * 3. 页面 `onActivityPaused` 时（息屏 / 切走 / 分屏失焦都走这里）。
 *
 * 页面（关于页、在线时长页）只读取最近一次上报回来的数据，**不触发上报**。
 *
 * 每 [PERSIST_INTERVAL_MS] 只把「已在线但还没落账」的时间写进本地账本，**不联网**，
 * 进程被杀最多丢这么多。
 */
object OnlineRecorder {

    /** 运行中定时上传间隔 */
    private const val UPLOAD_INTERVAL_MS = 5 * 60_000L

    /** 把「已在线但还没落账」的时间写进本地账本的间隔：进程被杀最多丢这么多 */
    private const val PERSIST_INTERVAL_MS = 60_000L

    /**
     * 启动时至少计的秒数。
     * 排行榜会过滤掉 0 秒的设备，不补这一秒的话，刚装上的设备要等实际累计出
     * 时长才会出现在榜上（用户会以为「怎么没有我」）。
     */
    private const val MIN_STARTUP_SECONDS = 1L

    private val uploadLock = Mutex()

    private var job: Job? = null
    private var scope: CoroutineScope? = null

    /**
     * 处于 resumed 状态的页面数；为 0 表示 App 已不在前台
     * （息屏、切走、分屏失焦、被弹窗盖住都会让计数归零）。
     */
    private var resumedActivities = 0

    /** 本轮「连续在线」的起点（elapsedRealtime）；为 null 表示当前不在线 */
    private var activeSince: Long? = null

    private var lastUploadAt = 0L

    fun start(scope: CoroutineScope, application: Application) {
        if (job != null) return
        this.scope = scope
        // 用 resumed/paused 成对计数，而不是 started/stopped：
        // onPause 是「离开」的信号，但它**不保证**后面一定有一次 onStart
        // （系统权限弹窗、分屏失焦等），所以恢复计时必须挂 onResume，
        // 否则一次弹窗就会让计时永久停摆。
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumedActivities++
                syncActiveState()
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
                syncActiveState()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        // 听书由前台服务驱动：开始/停止朗读都要重算在线状态，
        // 否则「后台停止朗读」没人重算，计时会一直涨。
        // 这里只重算、不上传 —— 上传点只保留上面那三个。
        TtsPlaybackController.state.observeForever { syncActiveState(notify = false) }

        job = scope.launch {
            // 启动就强制报活一次：既补传上次残留的记录，也让设备立刻登记下来
            // （在线人数、加入天数、排行榜可见性都依赖 ol_users 里有这条记录）。
            // 用 force 是因为本地账本通常为空，普通上传会直接跳过、连请求都不发。
            // 再补足 1 秒，否则刚装上的设备会因为「0 秒被过滤」而上不了榜。
            OnlineStore.ensureAtLeast(MIN_STARTUP_SECONDS)
            upload(force = true)
            while (isActive) {
                delay(PERSIST_INTERVAL_MS)
                // 只落账，不联网；上传交给 5 分钟那次判断
                persistActive()
                if (SystemClock.elapsedRealtime() - lastUploadAt >= UPLOAD_INTERVAL_MS) {
                    upload()
                }
            }
        }
    }

    /** 有页面处于 resumed 状态，或正在听书，都算在线 */
    private fun isOnline(): Boolean =
        resumedActivities > 0 || TtsPlaybackController.isPlaying()

    /**
     * 让记账状态与实际在线状态保持一致；从在线变为离线时结算本轮时长。
     *
     * @param notify 变为离线时是否顺带立即上传。
     *   只有 [onActivityPaused]（真正的「离开」）传 true；
     *   听书状态变化只重算状态，上传交给 5 分钟那次，避免多出上传点。
     */
    @Synchronized
    private fun syncActiveState(notify: Boolean = true) {
        if (isOnline()) {
            if (activeSince == null) activeSince = SystemClock.elapsedRealtime()
            return
        }
        val since = activeSince ?: return
        activeSince = null
        OnlineStore.add((SystemClock.elapsedRealtime() - since) / 1000)
        if (notify) scope?.launch { upload() }
    }

    /** 把本轮已在线的时间写进本地账本（不改变在线状态、不联网） */
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
     * [lastUploadAt] 只在**成功之后**才更新：失败（断网、超时）不占用上传间隔，
     * 下一个 60 秒 tick 就会重试。否则启动时那次「立即上传」一旦失败，
     * 就要再等满 5 分钟才有下一次机会。
     *
     * @param force 为真时即使本地没有记录也发一次（启动报到、刷新在线人数用）
     */
    private suspend fun upload(force: Boolean = false): OnlineInfo? = uploadLock.withLock {
        val items = OnlineStore.snapshot()
        if (items.isEmpty() && !force) return@withLock null
        val online = try {
            OnlineApi.report(onlineDeviceId, items)
        } catch (e: Exception) {
            Log.e("在线时长上报失败: ${e.message}")
            null
        } ?: return@withLock null
        lastUploadAt = SystemClock.elapsedRealtime()
        OnlineStore.subtract(items)
        OnlineInfos.onlineInfo.postValue(online)
        online
    }
}
