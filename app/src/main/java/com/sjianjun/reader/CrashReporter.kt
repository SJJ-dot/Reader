package com.sjianjun.reader

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sjianjun.reader.preferences.globalConfig

/**
 * 崩溃上报初始化。
 *
 * 调用时机：用户同意隐私说明之后（[com.sjianjun.reader.module.main.MainActivity] 的 initialize()）。
 * 默认关闭由 App.onCreate 的 setCrashlyticsCollectionEnabled(false) 保证，
 * 因此每次启动都要由这里按当前同意状态重新开启，同意前发生的崩溃不会被上报。
 */
fun initCrashReporter() {
    val accepted = globalConfig.privacyPolicyAccepted.value == true
    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(accepted)
}
