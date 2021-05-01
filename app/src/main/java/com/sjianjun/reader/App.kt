package com.sjianjun.reader

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.sjianjun.reader.matrix.DynamicConfigImplDemo
import com.sjianjun.reader.matrix.TestPluginListener
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.ActivityManger
import com.tencent.matrix.Matrix
import com.tencent.matrix.batterycanary.BatteryMonitorPlugin
import com.tencent.matrix.batterycanary.monitor.BatteryMonitorCallback.BatteryPrinter
import com.tencent.matrix.batterycanary.monitor.BatteryMonitorConfig
import com.tencent.matrix.batterycanary.monitor.feature.JiffiesMonitorFeature
import com.tencent.matrix.iocanary.IOCanaryPlugin
import com.tencent.matrix.iocanary.config.IOConfig
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
            writeToFile = false
            val dir = externalCacheDir
            if (dir != null) {
                writeToFileDir = File(dir, "alog")
            }
            writeToFileDirName = "reader"
        }

        initMatrix(this)
    }

    private fun initMatrix(application: App) {

        val config = BatteryMonitorConfig.Builder()
            .enable(JiffiesMonitorFeature::class.java)
            .enableStatPidProc(true)
            .greyJiffiesTime(30 * 1000L)
            .setCallback(BatteryPrinter())
            .build()

        val plugin = BatteryMonitorPlugin(config)

        val builder: Matrix.Builder = Matrix.Builder(application) // build matrix

        builder.patchListener(TestPluginListener(this)) // add general pluginListener

        val dynamicConfig = DynamicConfigImplDemo() // dynamic config


        // init plugin

        // init plugin
        val ioCanaryPlugin = IOCanaryPlugin(
            IOConfig.Builder()
                .dynamicConfig(dynamicConfig)
                .build()
        )
        //add to matrix
        //add to matrix
        builder.plugin(ioCanaryPlugin).plugin(plugin)

        //init matrix

        //init matrix
        Matrix.init(builder.build())

        // start plugin

        // start plugin
        ioCanaryPlugin.start()
    }

    companion object {
        lateinit var app: App
    }
}