package com.sjianjun.reader

import android.content.Context
import com.tencent.bugly.crashreport.CrashReport
import com.wanjian.cockroach.Cockroach
import com.wanjian.cockroach.ExceptionHandler
import sjj.alog.Log

fun handleDefaultException(context: Context) {
    val default = Thread.getDefaultUncaughtExceptionHandler()
    Cockroach.install(context, object : ExceptionHandler() {
        override fun onUncaughtExceptionHappened(thread: Thread?, throwable: Throwable?) {
            Log.e("捕获到异常 ",throwable)
        }

        override fun onBandageExceptionHappened(throwable: Throwable?) {
            Log.e("捕获到异常 ",throwable)
        }

        override fun onEnterSafeMode() {
            Log.e("进入安全模式")
        }

        override fun onMayBeBlackScreen(e: Throwable) {
            Log.e("捕获到异常 ",e)
            default?.uncaughtException(Thread.currentThread(),e)
        }
    })
    //
    CrashReport.initCrashReport(context, "d3d6da5bd7", BuildConfig.DEBUG);
}