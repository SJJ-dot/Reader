package com.sjianjun.reader.preferences

import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import com.sjianjun.reader.bean.ReleasesInfo
import com.tencent.mmkv.MMKV

/**
 * 与 [globalConfig] 完全独立的偏好存储（独立 MMKV 实例 AppEssentials）。
 * 仅保留“保底更新 / 基础夜间模式”这类最基础、且更新子进程必须读取的配置，
 * 即使 globalConfig 所在 MMKV 损坏，也不影响更新检查与基础主题。
 */
val appEssentials by lazy { AppEssentials() }

class AppEssentials : DelegateSharedPref(MMKV.mmkvWithID("AppEssentials", MMKV.MULTI_PROCESS_MODE)) {
    /** github 发布的版本信息 */
    var releasesInfo by dataPref<ReleasesInfo?>("releasesInfo_new222", null)

    /** 用户忽略的版本号，该版本不再自动提示更新 */
    var ignoredUpdateVersion by strPref("ignoredUpdateVersion", null)

    /** 日间/夜间模式 */
    var appDayNightMode by intPref("appDayNightMode", MODE_NIGHT_NO)

    /** 连续崩溃次数（启动闪屏页据此决定是否进入保底更新模式） */
    var consecutiveCrashes by intPref("consecutiveCrashes", 0)
}
