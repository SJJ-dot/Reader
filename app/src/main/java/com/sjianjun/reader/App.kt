package com.sjianjun.reader


import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.sjianjun.reader.preferences.appEssentials
import com.sjianjun.reader.utils.ActivityManger
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import sjj.alog.Config
import sjj.alog.Log
import java.io.File

class App : Application() {
    /**
     * 应用级协程作用域，用于不依赖页面/ViewModel 生命周期的后台任务。
     * 不能用 viewModelScope：ViewModel 销毁时会被取消，任务还没执行就没了
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        MMKV.initialize(this)
        handleDefaultException(this)
        // 必须在进程内第一次 Log.* 调用之前配置 alog：
        // Log.get() 首次调用会用 Config.getDefaultConfig() 构造 Logger/LogUtils，
        // 而 LogUtils 只在构造那一刻按 writeToFile 决定是否创建 LogFile（之后改配置也补不回来，
        // 日志会被静默丢弃且不报错）。放在这里才能覆盖 Splash/MainActivity 的启动日志。
        val alogDir = File(externalCacheDir ?: cacheDir, "alog")
        Config.getDefaultConfig().apply {
            consolePrintEnable = BuildConfig.DEBUG
            deleteOldLogFile = true
            consolePrintAllLog = true
            writeToFile = true
            writeToFileDir = alogDir
        }

        Log.i("▶Launch App.onCreate 日志目录=$alogDir 进程启动 consecutiveCrashes ${appEssentials.consecutiveCrashes} -> ${appEssentials.consecutiveCrashes + 1}")
        appEssentials.consecutiveCrashes += 1
        ActivityManger.init(this)
        AppCompatDelegate.setDefaultNightMode(appEssentials.appDayNightMode)
    }

    /** 当前进程是否为更新子进程 */
    private fun isUpdateProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val name = am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        return name != null && name.endsWith(":update")
    }


    companion object {
        lateinit var app: App
    }
}
