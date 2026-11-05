package com.sjianjun.reader

import android.content.Context
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.nickname
import com.tencent.bugly.crashreport.CrashReport
import java.util.UUID


fun handleDefaultException(context: Context) {
    //
    CrashReport.initCrashReport(context.applicationContext, BuildConfig.BUGLY_APP_ID, BuildConfig.DEBUG)

}
