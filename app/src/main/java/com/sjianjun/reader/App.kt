package com.sjianjun.reader

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.ActivityManger
import leakcanary.LeakCanaryProcess

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (LeakCanaryProcess.isInAnalyzerProcess(this)) {
            //LeakCanary 分析内存堆转储文件时，单独启动一个进程，尽量避免影响主进程
            return
        }
        app = this
        handleDefaultException(this)
        ActivityManger.init(this)
        AppCompatDelegate.setDefaultNightMode(globalConfig.appDayNightMode)

    }


    companion object {
        lateinit var app: App
    }
}