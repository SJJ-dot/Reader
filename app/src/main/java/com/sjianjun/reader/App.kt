package com.sjianjun.reader

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.github.liuyueyi.quick.transfer.constants.TransType
import com.sjianjun.reader.mqtt.MqttUtil
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.BookUseCase
import com.sjianjun.reader.utils.ActivityManger
import com.sjianjun.reader.utils.ChineseUtils
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sjj.alog.Config
import sjj.alog.Log
import java.io.File

class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
        resetBookLoadingState()
        initialize()
    }

    /**
     * 清理书籍残留的加载中状态，避免异常退出后书籍一直无法加载
     */
    private fun resetBookLoadingState() {
        appScope.launch(Dispatchers.IO) {
            runCatching { BookUseCase.resetBookLoadingState() }
                .onFailure { Log.e("重置书籍加载状态失败", it) }
        }
    }

    fun initialize() {
        if (globalConfig.privacyPolicyAccepted.value != true) return
        if (!first) return
        first = false
        handleDefaultException(this)
        Config.getDefaultConfig().apply {
            consolePrintEnable = BuildConfig.DEBUG
            deleteOldLogFile = true
            consolePrintAllLog = true
            writeToFile = false
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
