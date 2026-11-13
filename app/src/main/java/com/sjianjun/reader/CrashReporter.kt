package com.sjianjun.reader

import android.util.Log
import com.sjianjun.reader.preferences.globalConfig
import com.umeng.commonsdk.UMConfigure

/**
 * 崩溃信息收集（友盟 U-APM）。
 *
 * 合规流程（官方要求）：
 * 1. [preInitCrashReporter] 在 App.onCreate 中调用，仅预初始化，不采集设备信息、不上报数据；
 * 2. [initCrashReporter] 只在用户同意隐私说明之后（MainActivity.initialize()）调用正式初始化，
 *    此时 SDK 才开始采集并上报崩溃/ANR 信息，不同意则永不调用；
 * 3. 已同意的用户后续冷启动仍需依次调用 preInit 与 init（init 可延迟，但不能省略）。
 */
private val umengAppKey: String get() = BuildConfig.UMENG_APP_KEY
private val umengChannel: String get() = BuildConfig.UMENG_CHANNEL

fun preInitCrashReporter() {
    if (umengAppKey.isBlank()) {
        Log.w("CrashReporter", "友盟 AppKey 未配置，跳过预初始化")
        return
    }
    UMConfigure.preInit(App.app, umengAppKey, umengChannel)
}

fun initCrashReporter() {
    if (globalConfig.privacyPolicyAccepted.value != true) return
    if (umengAppKey.isBlank()) {
        Log.w("CrashReporter", "友盟 AppKey 未配置，跳过初始化")
        return
    }
    UMConfigure.setLogEnabled(BuildConfig.DEBUG)
    UMConfigure.init(
        App.app,
        umengAppKey,
        umengChannel,
        UMConfigure.DEVICE_TYPE_PHONE,
        ""
    )
}
