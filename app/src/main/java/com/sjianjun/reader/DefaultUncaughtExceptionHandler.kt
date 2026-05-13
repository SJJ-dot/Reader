package com.sjianjun.reader

import android.content.Context
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.bugly.crashreport.CrashReport.UserStrategy



fun handleDefaultException(context: Context) {
    val strategy = UserStrategy(context)
    CrashReport.initCrashReport(context, "d3d6da5bd7", BuildConfig.DEBUG, strategy)

}