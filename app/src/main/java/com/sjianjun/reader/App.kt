package com.sjianjun.reader

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.ActivityManger
import com.sjianjun.reader.utils.AppDirUtil
import com.tencent.mmkv.MMKV
import com.umeng.commonsdk.UMConfigure
import me.weishu.reflection.Reflection
import sjj.alog.Config
import sjj.alog.Log
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
        if (globalConfig.hasPermission) {
            Log.i("APP启动，已有权限重新初始化")
            AppDirUtil.init(this)
        }
    }

    companion object {
        lateinit var app: App
    }
}