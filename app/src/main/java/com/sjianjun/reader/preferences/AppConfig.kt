package com.sjianjun.reader.preferences

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.URL_BOOK_SOURCE_DEF
import com.tencent.mmkv.MMKV
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
    val readerBrightnessMaskColor = intLivedata("readerBrightnessMaskColor")

    /**
     * 阅读器 内容字体大小 、章节名称+4
     */
    val readerFontSize = intLivedata("readerFontSize", 22)

    /**
     * 阅读器 内容字体行间距
     */
    val readerLineSpacing = floatLivedata("readerLineSpacing2", 0.5f)

    /**
     * 阅读器 页面样式 位置索引
     */
    val readerPageStyle = intLivedata("readerPageStyle", 1)

    /**
     * 阅读器 页面样式 位置索引
     */
    val readerPageMode = intLivedata("readerPageMode", 0)

    /**
     * 上一次使用的深色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastDarkTheme = intLivedata("lastDarkTheme")

    /**
     * 上一次使用的浅色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastLightTheme = intLivedata("lastLightTheme")

    var bookSourceImportUrlsNet by dataPref(
        "bookSourceImportUrlsNet",
        listOf(URL_BOOK_SOURCE_DEF)
    )

    var bookSourceImportUrlsLoc by dataPref(
        "bookSourceImportUrlsLoc",
        mutableListOf<String>()
    )

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
}

