package com.sjianjun.reader

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.ActivityManger
import com.tencent.mmkv.MMKV
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
        importSharedPreferences()
        handleDefaultException(this)
        ActivityManger.init(this)
        AppCompatDelegate.setDefaultNightMode(globalConfig.appDayNightMode)
        Config.getDefaultConfig().apply {
            consolePrintAllLog = true
            writeToFile = false
            val dir = externalCacheDir
            if (dir != null) {
                writeToFileDir = File(dir, "alog")
            }
            writeToFileDirName = "reader"
        }

    }

    private fun importSharedPreferences() {
        val defaultMMKV = MMKV.defaultMMKV()
        if (!defaultMMKV.getBoolean("importSharedPreferences", false)) {
            defaultMMKV.putBoolean("importSharedPreferences", true)
            execImportSharedPreferences("default")
            execImportSharedPreferences("adBlockList")
        }
    }

    private fun execImportSharedPreferences(name: String) {
        val old: SharedPreferences = getSharedPreferences("AppConfig_${name}", MODE_PRIVATE)
        val mmkvWithID = MMKV.mmkvWithID("AppConfig_${name}")
        mmkvWithID.importFromSharedPreferences(old)
        old.edit().clear().apply()
    }

    companion object {
        lateinit var app: App
    }
}