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
