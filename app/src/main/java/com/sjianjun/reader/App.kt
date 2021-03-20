package com.sjianjun.reader

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.ActivityManger
import sjj.alog.Config
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        handleDefaultException(this)
        ActivityManger.init(this)
        AppCompatDelegate.setDefaultNightMode(globalConfig.appDayNightMode)
        Config.getDefaultConfig().apply {
            consolePrintAllLog = true
            writeToFile = true
            val dir = externalCacheDir
            if (dir != null) {
                writeToFileDir = File(dir,"alog")
            }
            writeToFileDirName = "reader"
        }
    }


    companion object {
        lateinit var app: App
    }
}