package com.sjianjun.reader.preferences

import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import com.sjianjun.reader.bean.FontInfo
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.bean.SourceUrl
import com.sjianjun.reader.module.bookcity.HostStr
import com.sjianjun.reader.module.reader.ReaderClickAreaAction
import com.sjianjun.reader.utils.dp2Px
import com.tencent.mmkv.MMKV
import sjj.novel.view.reader.page.CustomPageStyleInfo
import sjj.novel.view.reader.page.PageStyle

val globalConfig by lazy { AppConfig("default") }

class AppConfig(val name: String) :
    DelegateSharedPref(MMKV.mmkvWithID("AppConfig_$name")) {
    /**
     * 阅读器九宫格点击区域配置
     */
    val readerClickAreaActions by lazy {
        dataLivedata("readerClickAreaActions", ReaderClickAreaAction.defaultConfig())
    }

    /**
     * github 发布的版本信息
     */
    var releasesInfo by dataPref<ReleasesInfo?>("releasesInfo_new222", null)

    /**
     * 用户忽略的版本号，该版本不再自动提示更新
     */
    var ignoredUpdateVersion by strPref("ignoredUpdateVersion", null)

    var appDayNightMode by intPref("appDayNightMode", MODE_NIGHT_NO)

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
     * 阅读器音量键翻页
     */
    val readerVolumeKeyPageTurn by lazy { boolLivedata("readerVolumeKeyPageTurn", false) }

    /**
     * 阅读器 TTS 引擎选择: "default" 或 "edge"
     */
    val readerTtsEngine by lazy { strLivedata("readerTtsEngine", "default") }

    /**
     * Edge TTS 选择的音色参数（例如 "zh-CN-XiaoxiaoNeural"）
     */
    val readerTtsVoice by lazy { strLivedata("readerTtsVoice", "zh-CN-XiaoxiaoNeural") }

    val readerTtsRate by lazy { intLivedata("readerTtsRate", 50) }

    // ==================== 朗读字幕（悬浮字幕） ====================

    /** 字幕文字大小（sp），12~28 */
    val ttsSubtitleFontSize by lazy { intLivedata("ttsSubtitleFontSize", 17) }

    /** 字幕整体不透明度（百分比 20~100） */
    val ttsSubtitleAlpha by lazy { intLivedata("ttsSubtitleAlpha", 85) }

    /** 字幕显示行数（1~4，默认 2） */
    val ttsSubtitleLines by lazy { intLivedata("ttsSubtitleLines", 2) }

    /**
     * 字幕文本偏移（毫秒）：正数让文本"提前"（解决声音先到、文字滞后），
     * 负数让文本"延后"（解决文字超前）。范围 -1500~1500。
     */
    val ttsSubtitleOffsetMs by lazy { intLivedata("ttsSubtitleOffsetMs", 0) }

    /**
     * 字幕浮窗纵向位置：屏幕高度的千分比（0~1000），-1 表示从未记录过。
     * 字幕条铺满屏幕宽度，只记录纵向位置；用比例以适应旋转与不同分辨率。
     */
    var ttsSubtitlePosYRatio by intPref("ttsSubtitlePosYRatio", -1)

    /** 上次朗读结束时是否显示字幕浮窗：下次开始朗读时据此自动恢复 */
    var ttsSubtitleVisible by boolPref("ttsSubtitleVisible", false)

    /**
     * 阅读器亮度：1~255，-1表示不主动调节亮度，跟随系统亮度。
     */
    val readerBrightnessPercent by lazy { intLivedata("readerBrightnessPercent", -1) }
    val readerScreenKeepOn by lazy { boolLivedata("readerScreenKeepOn", false) }

    /**
     * 阅读器排版模式:
     * 0=默认横排左起, 1=横排右起, 2=竖排左起, 3=竖排右起
     */
    val readerTypesettingMode by lazy { intLivedata("readerTypesettingMode", 0) }

    /**
     * 横排文字网格对齐：以 referenceWidth 为网格基准，每个字符起点对齐网格
     */
    val readerTextGridAlign by lazy { boolLivedata("readerTextGridAlign", false) }
    val readerFullWidthConvert by lazy { boolLivedata("readerFullWidthConvert", true) }

    /**
     * 上一次使用的深色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastDarkTheme by lazy { strLivedata("lastDarkThemeStr", PageStyle.defNight.id) }

    /**
     * 上一次使用的浅色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastLightTheme by lazy { strLivedata("lastLightThemeStr", PageStyle.defDay.id) }

    /**
     * 旧版本的订阅链接存储（MMKV），现已迁移到数据库；
     * 仅保留用于首次启动时把老数据迁移到数据库，请勿再写入
     */
    var bookSourceListUser by dataPref("bookSourceListUser2", emptyList<SourceUrl>())

    val customPageStyleInfoList by lazy {
        dataLivedata<List<CustomPageStyleInfo>>("customPageStyleInfosStr2", emptyList())
    }

    var bookCityUrl by dataPref("bookCityUrl", "https://m.qidian.com")

    //本次启动之后书城访问的所有的URL
    var hostBlacklist by dataPref("hostBlacklistHostStr", mutableListOf<HostStr>())

    var hostWhitelist by dataPref("hostWhitelistHostStr", mutableListOf<HostStr>())

    /**
     * 跨域配置: 0=询问, 1=允许, 2=拒绝
     */
    var crossOriginMode by intPref("crossOriginMode", 0)

    var deviceID by strPref("deviceID", null)

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

    /**
     * 是否自动检查更新
     */
    val autoCheckUpdate by lazy { boolLivedata("autoCheckUpdate", true) }

    /**
     * 是否默认使用阅读模式打开网页书籍
     */
    val autoBrowserReadMode by lazy { boolLivedata("autoBrowserReadMode", true) }

    /**
     * 浏览器阅读模式磁盘缓存大小（单位 MB），默认 50MB
     */
    var browserReadModeCacheSizeMB by intPref("browserReadModeCacheSizeMB", 50)

    /**
     * 用户是否已同意隐私政策
     */
    val privacyPolicyAccepted by lazy { boolLivedata("privacyPolicyAccepted1", false) }

    var webFontZoomSize by intPref("webFontZoomSize", 100)

    /**
     * 浏览器 UA 模式：0=手机版（默认），1=电脑版
     */
    var webUaMode by intPref("webUaMode", 0)

    /** 浏览器搜索引擎下标：0百度 1必应 2谷歌 3搜狗 4 360 5神马 */
    var webSearchEngine by intPref("webSearchEngine", 0)

    /** 阅读器顶部显示 CPU/内存信息 */
    val showCpuMemInfo by lazy { boolLivedata("showCpuMemInfo", true) }
}
