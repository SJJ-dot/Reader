package com.sjianjun.reader.module.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sjianjun.reader.App
import com.sjianjun.reader.preferences.appEssentials
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.checkUpdate
import kotlinx.coroutines.launch
import sjj.alog.Log

/**
 * 启动闪屏页（LAUNCHER，主进程）。更新检查只在这里发起，MainActivity 不参与检查。
 * - 正常（无连续崩溃记录）：立即拉起 MainActivity 进入应用；更新检查在应用级作用域后台进行，
 *   发现可用更新时再新拉起透明弹窗宿主 Activity 浮在 MainActivity 之上（不影响进入应用）；
 * - 崩溃保底模式（连续崩溃达到阈值）：阻塞等待更新检查结束（最多 15s），
 *   有更新则等用户完成下载 / 取消 / 报错后再进入主页；无更新 / 超时 / 失败直接进入主页。
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashes = appEssentials.consecutiveCrashes
        Log.i("▶Launch Splash.onCreate 开始 crashes=$crashes isTaskRoot=$isTaskRoot flags=${Integer.toHexString(intent.flags)} taskId=$taskId savedInstanceState=${savedInstanceState != null}")

        if (crashes >= FALLBACK_CRASH_THRESHOLD) {
            // 崩溃保底：阻塞等待更新检查结束
            Log.i("▶Launch Splash 进入保底模式，先等 checkUpdate 完成（此时闪屏页无内容）")
            lifecycleScope.launch {
                checkUpdate(fromUser = false)
                Log.i("▶Launch Splash checkUpdate 返回，准备 startMain")
                startMain()
            }
        } else {
            // 正常：立即进入主页，不阻塞
            Log.i("▶Launch Splash 正常模式，立即 startMain")
            startMain()
        }
    }

    private fun startMain() {
        // 不加 FLAG_ACTIVITY_CLEAR_TOP：冷启动时栈内没有 MainActivity，该 flag 本就是空操作；
        // 而 LAUNCHER intent 被再次投递、Splash 被压到已有任务之上时，CLEAR_TOP 会连 Splash 自己一起清除，
        // 且 MainActivity 是 standard（无 SINGLE_TOP）会被销毁并重建，中间出现窗口空档导致桌面闪烁/需再次点击。
        // 重复实例的收敛交给 MainActivity.onCreate 的 ActivityManger.finishSameType 处理。
        Log.i("▶Launch startMain 调用 isTaskRoot=$isTaskRoot isFinishing=$isFinishing")
        startActivity(Intent(this, MainActivity::class.java))
        Log.i("▶Launch startMain startActivity 已返回，finish Splash")
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i("▶Launch Splash.onNewIntent flags=${Integer.toHexString(intent.flags)} isTaskRoot=$isTaskRoot")
    }

    companion object {
        /** 连续崩溃达到该次数后，下次启动进入保底模式 */
        private const val FALLBACK_CRASH_THRESHOLD = 3
    }
}
