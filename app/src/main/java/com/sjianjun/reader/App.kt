package com.sjianjun.reader

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.ActivityManger
import com.tencent.mmkv.MMKV
import com.umeng.commonsdk.UMConfigure
import me.weishu.reflection.Reflection
import sjj.alog.Config
import java.io.File

class App : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        Reflection.unseal(base);
    }
    override fun onCreate() {
        super.onCreate()
        app = this
        MMKV.initialize(this)
        UMConfigure.setLogEnabled(true)
        UMConfigure.preInit(this,"63520d0c88ccdf4b7e50c31f","")
        handleDefaultException(this)
        ActivityManger.init(this)
        AppCompatDelegate.setDefaultNightMode(globalConfig.appDayNightMode)
        Config.getDefaultConfig().apply {
            consolePrintAllLog = true
            writeToFile = true
            val dir = externalCacheDir
            if (dir != null) {
                writeToFileDir = File(dir, "alog")
            }
            writeToFileDirName = "reader"
        }
    }

    companion object {
        lateinit var app: App
    }
}