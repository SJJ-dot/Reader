package com.sjianjun.reader

import android.app.Application
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import com.sjianjun.reader.matrix.battery.BatteryCanaryInitHelper
import com.sjianjun.reader.matrix.config.DynamicConfigImplDemo
import com.sjianjun.reader.matrix.listener.TestPluginListener
import com.sjianjun.reader.matrix.resource.ManualDumpActivity
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.ActivityManger
import com.tencent.matrix.Matrix
import com.tencent.matrix.batterycanary.BatteryMonitorPlugin
import com.tencent.matrix.iocanary.IOCanaryPlugin
import com.tencent.matrix.iocanary.config.IOConfig
import com.tencent.matrix.resource.ResourcePlugin
import com.tencent.matrix.resource.config.ResourceConfig
import com.tencent.matrix.resource.config.ResourceConfig.DumpMode
import com.tencent.matrix.threadcanary.ThreadMonitor
import com.tencent.matrix.threadcanary.ThreadMonitorConfig
import com.tencent.matrix.trace.TracePlugin
import com.tencent.matrix.trace.config.TraceConfig
import com.tencent.matrix.util.MatrixLog
import com.tencent.sqlitelint.SQLiteLint
import com.tencent.sqlitelint.SQLiteLintPlugin
import com.tencent.sqlitelint.config.SQLiteLintConfig
import sjj.alog.Config
import sjj.alog.Log
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

        val dynamicConfig = DynamicConfigImplDemo()
        val matrixEnable: Boolean = dynamicConfig.isMatrixEnable()
        val fpsEnable: Boolean = dynamicConfig.isFPSEnable()
        val traceEnable: Boolean = dynamicConfig.isTraceEnable()


        Log.i("MatrixApplication.onCreate")

        val builder = Matrix.Builder(this)
        builder.patchListener(TestPluginListener(this))

        //trace

        //trace
        val traceConfig = TraceConfig.Builder()
            .dynamicConfig(dynamicConfig)
            .enableFPS(fpsEnable)
            .enableEvilMethodTrace(traceEnable)
            .enableAnrTrace(traceEnable)
            .enableStartup(traceEnable)
            .splashActivities("com.sjianjun.reader.matrix.SplashActivity;")
            .isDebug(true)
            .isDevEnv(false)
            .build()

        val tracePlugin = TracePlugin(traceConfig)
        builder.plugin(tracePlugin)

        if (matrixEnable) {

            //resource
            val intent = Intent()
            val mode = DumpMode.MANUAL_DUMP
            Log.i("Dump Activity Leak Mode=$mode")
            intent.setClassName(this.packageName, "com.tencent.mm.ui.matrix.ManualDumpActivity")
            val resourceConfig = ResourceConfig.Builder()
                .dynamicConfig(dynamicConfig)
                .setAutoDumpHprofMode(mode) //                .setDetectDebuger(true) //matrix test code
                //                    .set(intent)
                .setManualDumpTargetActivity(ManualDumpActivity::class.java.getName())
                .build()
            builder.plugin(ResourcePlugin(resourceConfig))
            ResourcePlugin.activityLeakFixer(this)

            //io
            val ioCanaryPlugin = IOCanaryPlugin(
                IOConfig.Builder()
                    .dynamicConfig(dynamicConfig)
                    .build()
            )
            builder.plugin(ioCanaryPlugin)


            // prevent api 19 UnsatisfiedLinkError
            //sqlite
            val sqlLiteConfig: SQLiteLintConfig
            sqlLiteConfig = try {
                SQLiteLintConfig(SQLiteLint.SqlExecutionCallbackMode.CUSTOM_NOTIFY)
            } catch (t: Throwable) {
                SQLiteLintConfig(SQLiteLint.SqlExecutionCallbackMode.CUSTOM_NOTIFY)
            }
            builder.plugin(SQLiteLintPlugin(sqlLiteConfig))
            val threadMonitor = ThreadMonitor(ThreadMonitorConfig.Builder().build())
            builder.plugin(threadMonitor)
            val batteryMonitorPlugin: BatteryMonitorPlugin = BatteryCanaryInitHelper.createMonitor()
            builder.plugin(batteryMonitorPlugin)
        }

        Matrix.init(builder.build())

        //start only startup tracer, close other tracer.

        //start only startup tracer, close other tracer.
        tracePlugin.start()
        Matrix.with().getPluginByClass(ThreadMonitor::class.java).start()
//        Matrix.with().getPluginByClass(BatteryMonitor.class).start();
        //        Matrix.with().getPluginByClass(BatteryMonitor.class).start();
        Log.i("Matrix.HackCallback end:${System.currentTimeMillis()}")
    }

    companion object {
        lateinit var app: App
    }
}