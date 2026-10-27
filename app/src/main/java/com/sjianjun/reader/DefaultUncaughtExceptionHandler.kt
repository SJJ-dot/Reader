package com.sjianjun.reader

import android.content.Context
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.nickname
import com.tencent.bugly.crashreport.CrashReport
import java.util.UUID


fun handleDefaultException(context: Context) {
    //
    var deviceID = globalConfig.deviceID
    if (deviceID == null) {
        deviceID = UUID.randomUUID().toString().replace("-", "")
        globalConfig.deviceID = deviceID
    }
    CrashReport.initCrashReport(context, BuildConfig.BUGLY_APP_ID, BuildConfig.DEBUG)
    CrashReport.setUserId(context, deviceID.nickname)
    CrashReport.setDeviceId(context, deviceID.nickname)

}
