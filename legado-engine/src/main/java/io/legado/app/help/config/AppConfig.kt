package io.legado.app.help.config

import io.legado.app.constant.AppConst
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isNightMode
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import io.legado.app.utils.sysConfiguration
import splitties.init.appCtx

@Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
object AppConfig {
    var userAgent: String = getPrefUserAgent()
    var isEInkMode = appCtx.getPrefString(PreferKey.themeMode) == "3"
    var themeMode = appCtx.getPrefString(PreferKey.themeMode, "0")
    var recordLog = appCtx.getPrefBoolean(PreferKey.recordLog)

    var isNightTheme: Boolean
        get() = when (themeMode) {
            "1" -> false
            "2" -> true
            "3" -> false
            else -> sysConfiguration.isNightMode
        }
        set(value) {
            if (isNightTheme != value) {
                if (value) {
                    appCtx.putPrefString(PreferKey.themeMode, "2")
                } else {
                    appCtx.putPrefString(PreferKey.themeMode, "1")
                }
            }
        }


    // 书籍保存位置
    var defaultBookTreeUri: String?
        get() = appCtx.getPrefString(PreferKey.defaultBookTreeUri)
        set(value) {
            if (value.isNullOrEmpty()) {
                appCtx.removePref(PreferKey.defaultBookTreeUri)
            } else {
                appCtx.putPrefString(PreferKey.defaultBookTreeUri, value)
            }
        }


    var threadCount: Int
        get() = appCtx.getPrefInt(PreferKey.threadCount, 16)
        set(value) {
            appCtx.putPrefInt(PreferKey.threadCount, value)
        }

    // 添加本地选择的目录
    var importBookPath: String?
        get() = appCtx.getPrefString("importBookPath")
        set(value) {
            if (value == null) {
                appCtx.removePref("importBookPath")
            } else {
                appCtx.putPrefString("importBookPath", value)
            }
        }

    var elevation: Int
        get() = if (isEInkMode) 0 else appCtx.getPrefInt(
            PreferKey.barElevation,
            AppConst.sysElevation
        )
        set(value) {
            appCtx.putPrefInt(PreferKey.barElevation, value)
        }

    // 是否启用自定义导出 default->false
    var enableCustomExport: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enableCustomExport, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableCustomExport, value)
        }

    var exportType: Int
        get() = appCtx.getPrefInt(PreferKey.exportType)
        set(value) {
            appCtx.putPrefInt(PreferKey.exportType, value)
        }
    private fun getPrefUserAgent(): String {
        val ua = appCtx.getPrefString(PreferKey.userAgent)
        if (ua.isNullOrBlank()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        }
        return ua
    }


}

