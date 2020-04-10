package com.sjianjun.reader

import android.content.Context
import com.tencent.bugly.crashreport.CrashReport

fun handleDefaultException(context: Context) {
    CrashReport.initCrashReport(context, "d3d6da5bd7", true);
}