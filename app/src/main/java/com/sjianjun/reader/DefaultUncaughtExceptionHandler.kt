package com.sjianjun.reader

import android.content.Context
import com.tencent.bugly.Bugly
import com.tencent.bugly.crashreport.CrashReport
import com.wanjian.cockroach.Cockroach
import com.wanjian.cockroach.ExceptionHandler
import sjj.alog.Log

fun handleDefaultException(context: Context) {
    //
//    CrashReport.initCrashReport(context, "d3d6da5bd7", BuildConfig.DEBUG);
    //检查应用更新 崩溃上报
    Bugly.init(context,"d3d6da5bd7",BuildConfig.DEBUG)
//    val default = Thread.getDefaultUncaughtExceptionHandler()
//    Cockroach.install(context, object : ExceptionHandler() {
//        override fun onUncaughtExceptionHappened(thread: Thread?, throwable: Throwable?) {
//            Log.e("捕获到异常 ", Exception(throwable))
//            CrashReport.postCatchedException(throwable, thread)
//        }
//
//        override fun onBandageExceptionHappened(throwable: Throwable?) {
//            Log.e("捕获到异常 ", Exception(throwable))
//            CrashReport.postCatchedException(throwable)
//        }
//
//        override fun onEnterSafeMode() {
//            Log.e("进入安全模式")
//        }
//
//        override fun onMayBeBlackScreen(throwable: Throwable) {
//            Log.e("捕获到异常 ", Exception(throwable))
////            CrashReport.postCatchedException(e)
//            default?.uncaughtException(Thread.currentThread(), throwable)
//        }
//    })

}