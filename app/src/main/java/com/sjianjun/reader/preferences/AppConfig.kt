package com.sjianjun.reader.preferences

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.URL_BOOK_SOURCE_DEF
import com.sjianjun.reader.bean.FontInfo
import com.sjianjun.reader.module.bookcity.HostStr
import com.tencent.mmkv.MMKV
import sjj.novel.view.reader.page.CustomPageStyleInfo
import sjj.novel.view.reader.page.PageStyle
import java.util.*

val globalConfig by lazy { AppConfig("default") }

class AppConfig(val name: String) :
    DelegateSharedPref(MMKV.mmkvWithID("AppConfig_$name")) {

    var hasPermission by boolPref("hasPermission", false)

    /**
     * github 发布的版本信息
     */
    var releasesInfo by dataPref<ReleasesInfo?>("releasesInfo_new222", null)
    var releasesInfoGithub by dataPref<ReleasesInfo?>("releasesInfoGithub", null)

    var appDayNightMode by intPref("appDayNightMode", MODE_NIGHT_NO)

    val qqAuthLoginUri = MutableLiveData<Uri>()

    /**
     * 阅读器亮度蒙层的颜色
     */
    val readerBrightnessMaskColor by lazy { intLivedata("readerBrightnessMaskColor") }

    /**
     * 阅读器 内容字体大小 、章节名称+4
     */
    val readerFontSize by lazy { intLivedata("readerFontSize", 22) }

    /**
     * 阅读器 内容字体行间距
     */
    val readerLineSpacing by lazy { floatLivedata("readerLineSpacing2", 0.5f) }

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
     * 上一次使用的深色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastDarkTheme by lazy { strLivedata("lastDarkThemeStr", PageStyle.defNight.id) }

    /**
     * 上一次使用的浅色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastLightTheme by lazy { strLivedata("lastLightThemeStr", PageStyle.defDay.id) }

    var bookSourceListUser by dataPref("bookSourceListUser", listOf<String>())

    var bookSourceDef by dataPref("bookSourceDef", URL_BOOK_SOURCE_DEF)

    var webdavUrl by strPref("webdavUrl", "https://dav.jianguoyun.com/dav/")
    var webdavUsername by strPref("webdavUsername", null)
    var webdavPassword by strPref("webdavPassword", null)
    var webdavSubdir by strPref("webdavSubdir", "reader")

    var webdavHasCfg by boolPref("webdavHasCfg")

    val webDavId: String
        get() {
            if (!contains("webDavId")) {
                edit { putString("webDavId", UUID.randomUUID().toString()) }
            }
            return getString("webDavId", null)!!
        }

    val customPageStyleInfoList by lazy {
        dataLivedata<List<CustomPageStyleInfo>>("customPageStyleInfosStr2", emptyList())
    }

    var bookCityUrlHistoryList by dataPref("bookCityUrlHistoryList", mutableListOf("https://m.qidian.com"))
    var bookCityUrl by dataPref("bookCityUrl", "https://m.qidian.com")

    //本次启动之后书城访问的所有的URL
    var hostBlacklist by dataPref("hostBlacklistHostStr", mutableListOf<HostStr>())
    var hostWhitelist by dataPref("hostWhitelistHostStr", mutableListOf<HostStr>())
}

