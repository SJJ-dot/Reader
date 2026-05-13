package com.sjianjun.reader

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.sjianjun.reader.preferences.globalConfig
import com.tencent.mmkv.MMKV
import sjj.alog.Config
import java.io.File

class App : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
    }
    override fun onCreate() {
        super.onCreate()
        app = this
        MMKV.initialize(this)
        handleDefaultException(this)
        AppCompatDelegate.setDefaultNightMode(globalConfig.appDayNightMode)
        Config.getDefaultConfig().apply {
            deleteOldLogFile = true
            consolePrintAllLog = true
            writeToFile = true
            writeToFileDir = File(externalCacheDir, "alog")
        }
    }


    companion object {
        lateinit var app: App
    }
}