package com.sjianjun.reader

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.pgyer.pgyersdk.PgyerSDKManager
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.ActivityManger
import com.sjianjun.reader.utils.AppDirUtil
import com.tencent.mmkv.MMKV
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
        importSharedPreferences()
        handleDefaultException(this)
        initPagerSdk(this)
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

    private fun initPagerSdk(application: Application){
        PgyerSDKManager
            .Init()
            .setContext(application)
            .start()
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