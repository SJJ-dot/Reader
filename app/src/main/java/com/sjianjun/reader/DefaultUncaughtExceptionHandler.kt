package com.sjianjun.reader

import android.content.Context
import com.sjianjun.reader.preferences.globalConfig
import com.tencent.bugly.crashreport.CrashReport
import java.util.UUID


fun handleDefaultException(context: Context) {
    //
    var deviceID = globalConfig.deviceID
    if (deviceID == null){
        deviceID = UUID.randomUUID().toString().replace("-", "")
        globalConfig.deviceID = deviceID
    }
    CrashReport.initCrashReport(context, "d3d6da5bd7", BuildConfig.DEBUG)
    CrashReport.setDeviceId(context,deviceID)

}