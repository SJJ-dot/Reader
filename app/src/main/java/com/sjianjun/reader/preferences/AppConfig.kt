package com.sjianjun.reader.preferences

import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.URL_BOOK_SOURCE_DEF
import com.sjianjun.reader.bean.FontInfo
import com.sjianjun.reader.module.bookcity.HostStr
import com.sjianjun.reader.utils.dp2Px
import com.tencent.mmkv.MMKV
import sjj.novel.view.reader.page.CustomPageStyleInfo
import sjj.novel.view.reader.page.PageStyle

val globalConfig by lazy { AppConfig("default") }

class AppConfig(val name: String) :
    DelegateSharedPref(MMKV.mmkvWithID("AppConfig_$name")) {

    /**
     * github 发布的版本信息
     */
    var releasesInfo by dataPref<ReleasesInfo?>("releasesInfo_new222", null)

    var appDayNightMode by intPref("appDayNightMode", MODE_NIGHT_NO)

    /**
     * 阅读器亮度蒙层的颜色
     */
    val readerBrightnessMaskColor by lazy { intLivedata("readerBrightnessMaskColor") }

    /**
     * 阅读器 内容字体大小 、章节名称+4
     */
    val readerFontSize by lazy { intLivedata("readerFontSize3", 30.dp2Px) }

    /**
     * 阅读器 内容字体行间距
     */
    val readerLineSpacing by lazy { intLivedata("readerLineSpacing3", 5.dp2Px) }
    val readerParaSpacing by lazy { intLivedata("readerParaSpacing", 16.dp2Px) }
    val readerLetterSpacing by lazy { intLivedata("readerLetterSpacing", 0.dp2Px) }

    /**
     * 阅读器 页面样式 位置索引
     */
    val readerPageStyle by lazy { strLivedata("readerPageStyleStr", PageStyle.defDay.id) }

    val readerFontFamily by lazy { dataLivedata<FontInfo>("readerFontInfo", FontInfo.DEFAULT) }

    /**
     * 阅读器 页面样式 位置索引
     */
    val readerPageMode by lazy { intLivedata("readerPageMode", 0) }

    /**
     * 阅读器简繁转换模式: 0=关闭, 1=简体转繁体, 2=繁体转简体
     */
    val readerJianFanMode by lazy { intLivedata("readerJianFanMode", 0) }
    /**
     * 阅读器横竖屏切换: 0=跟随系统, 1=竖屏, 2=横屏，3=横屏自动
     */
    val readerOrientationMode by lazy { intLivedata("readerOrientationMode", 0) }

    /**
     * 阅读器排版模式:
     * 0=默认横排左起, 1=横排右起, 2=竖排左起, 3=竖排右起
     */
    val readerTypesettingMode by lazy { intLivedata("readerTypesettingMode", 0) }
    /**
     * 上一次使用的深色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastDarkTheme by lazy { strLivedata("lastDarkThemeStr", PageStyle.defNight.id) }

    /**
     * 上一次使用的浅色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastLightTheme by lazy { strLivedata("lastLightThemeStr", PageStyle.defDay.id) }

    var bookSourceListUser by dataPref("bookSourceListUser", listOf<String>())

    var bookSourceDef by dataPref("bookSourceDef", URL_BOOK_SOURCE_DEF)

    val customPageStyleInfoList by lazy {
        dataLivedata<List<CustomPageStyleInfo>>("customPageStyleInfosStr2", emptyList())
    }

    var bookCityUrlHistoryList by dataPref("bookCityUrlHistoryList", mutableListOf("https://m.qidian.com"))
    var bookCityUrl by dataPref("bookCityUrl", "https://m.qidian.com")

    //本次启动之后书城访问的所有的URL
    var hostBlacklist by dataPref("hostBlacklistHostStr", mutableListOf<HostStr>())

    var mqttClientId by strPref("mqtt_client_id", null)

    var admin by boolPref("admin", false)

    var readerSizeW by intPref("readerSizeW", 0)
    var readerSizeH by intPref("readerSizeH", 0)
}

