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

        if (appEssentials.consecutiveCrashes >= FALLBACK_CRASH_THRESHOLD) {
            // 崩溃保底：阻塞等待更新检查结束
            lifecycleScope.launch {
                checkUpdate(fromUser = false)
                startMain()
            }
        } else {
            // 正常：立即进入主页，不阻塞
            startMain()
        }
    }

    private fun startMain() {
        // 进入主页并复用已有实例、不堆叠：CLEAR_TOP 在本任务内复用已存在的 MainActivity。
        // 注意：这里【不要】用 FLAG_ACTIVITY_NEW_TASK —— SplashActivity 是 LAUNCHER 且自身会 finish()，
        // 加 NEW_TASK 会让系统把“启动器任务”的根记为 SplashActivity；进程被系统回收后再从桌面图标恢复时，
        // 会重建 SplashActivity 又立刻 finish()，容易留下空任务导致“图标闪一下就退出、要再点一次”。
        // 从 Activity 上下文启动本就不需要 NEW_TASK。
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }

    companion object {
        /** 连续崩溃达到该次数后，下次启动进入保底模式 */
        private const val FALLBACK_CRASH_THRESHOLD = 3
    }
}
