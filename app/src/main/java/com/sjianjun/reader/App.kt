package com.sjianjun.reader

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.github.liuyueyi.quick.transfer.constants.TransType
import com.sjianjun.reader.mqtt.MqttUtil
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.ActivityManger
import com.sjianjun.reader.utils.ChineseUtils
import com.tencent.mmkv.MMKV
import sjj.alog.Config
import java.io.File

class App : io.legado.app.App() {
    private var first = true
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        MMKV.initialize(this)
        ActivityManger.init(this)
        AppCompatDelegate.setDefaultNightMode(globalConfig.appDayNightMode)
        initialize()
    }

    fun initialize() {
        if (globalConfig.privacyPolicyAccepted.value != true) return
        if (!first) return
        first = false
        handleDefaultException(this)
        Config.getDefaultConfig().apply {
            deleteOldLogFile = true
            consolePrintAllLog = true
            writeToFile = true
            writeToFileDir = File(externalCacheDir, "alog")
        }
        Python.start(AndroidPlatform(this))
        MqttUtil.connect()
        when (globalConfig.readerJianFanMode.value) {
            1 -> {
                ChineseUtils.fixT2sDict()
                ChineseUtils.preLoad(true, TransType.SIMPLE_TO_TRADITIONAL)
            }

            2 -> ChineseUtils.preLoad(true, TransType.TRADITIONAL_TO_SIMPLE)
        }
    }


    companion object {
        lateinit var app: App
    }
}