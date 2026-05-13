package com.sjianjun.reader.preferences

import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.URL_BOOK_SOURCE_DEF
import com.tencent.mmkv.MMKV

val globalConfig by lazy { AppConfig("default") }

class AppConfig(val name: String) :
    DelegateSharedPref(MMKV.mmkvWithID("AppConfig_$name")) {
    /**
     * github 发布的版本信息
     */
    var releasesInfo by dataPref<ReleasesInfo?>("releasesInfo_new222", null)

    var appDayNightMode by intPref("appDayNightMode", MODE_NIGHT_NO)

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
     * 阅读器音量键翻页
     */
    val readerVolumeKeyPageTurn by lazy { boolLivedata("readerVolumeKeyPageTurn", false) }

    /**
     * 阅读器亮度：1~255，-1表示不主动调节亮度，跟随系统亮度。
     */
    val readerBrightnessPercent by lazy { intLivedata("readerBrightnessPercent", -1) }

    /**
     * 阅读器排版模式:
     * 0=默认横排左起, 1=横排右起, 2=竖排左起, 3=竖排右起
     */
    val readerTypesettingMode by lazy { intLivedata("readerTypesettingMode", 0) }

    var bookSourceListUser by dataPref("bookSourceListUser", listOf<String>())

    var bookSourceDef by dataPref("bookSourceDef", URL_BOOK_SOURCE_DEF)


    var bookCityUrlHistoryList by dataPref("bookCityUrlHistoryList", mutableListOf("https://m.qidian.com"))
    var bookCityUrl by dataPref("bookCityUrl", "https://m.qidian.com")


    var mqttClientId by strPref("mqtt_client_id", null)

    var admin by boolPref("admin", false)

    var readerSizeW by intPref("readerSizeW", 0)
    var readerSizeH by intPref("readerSizeH", 0)

    /**
     * 数据库存储目录。null表示内部存储默认目录。
     * null表示内部存储默认目录。
     */
    var databaseStorageDir by strPref("databaseStorageDir", null)

    /**
     * 书架布局类型：0=默认列表，1=网格
     */
    var shelfLayoutType by intPref("shelfLayoutType", 0)

    /**
     * 排行榜默认页签：online / recommend
     */
    var leaderboardTab by strPref("leaderboardTab", "online")
}

