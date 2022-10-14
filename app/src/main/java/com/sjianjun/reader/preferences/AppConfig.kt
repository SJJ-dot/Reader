package com.sjianjun.reader.preferences

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.utils.BOOK_SOURCE_QI_DIAN
import com.tencent.mmkv.MMKV

val globalConfig by lazy { AppConfig("default") }

class AppConfig(val name: String) :
    DelegateSharedPref(MMKV.mmkvWithID("AppConfig_$name")) {

    var hasPermission by boolPref("hasPermission", false)

    /**
     * github 发布的版本信息
     */
    var releasesInfo by strPref("releasesInfo", null)

    /**
     * 上次检查更新的时间
     */
    var lastCheckUpdateTime by longPref("lastCheckUpdateTime", 0)

    var appDayNightMode by intPref("appDayNightMode", MODE_NIGHT_NO)

    val bookCityDefaultSource = strLivedata("bookCityDefaultSource", BOOK_SOURCE_QI_DIAN)

    val qqAuthLoginUri = MutableLiveData<Uri>()

    /**
     * 阅读器亮度蒙层的颜色
     */
    val readerBrightnessMaskColor = intLivedata("readerBrightnessMaskColor")

    /**
     * 阅读器 内容字体大小 、章节名称+4
     */
    val readerFontSize = intLivedata("readerFontSize", 22)

    /**
     * 阅读器 内容字体行间距
     */
    val readerLineSpacing = floatLivedata("readerLineSpacing", 1.5f)

    /**
     * 阅读器 页面样式 位置索引
     */
    val readerPageStyle = intLivedata("readerPageStyle")

    /**
     * 上一次使用的深色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastDarkTheme = intLivedata("lastDarkTheme")

    /**
     * 上一次使用的浅色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastLightTheme = intLivedata("lastLightTheme")

    /**
     * 记录上一次启动时App版本
     */
    var lastAppVersion by intPref("lastAppVersion")
    var lastAppVersionName by strPref("lastAppVersionName")

    var bookSourceImportUrls by dataPref<MutableList<String>>("bookSourceImportUrl", mutableListOf())
}

