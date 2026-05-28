package io.legado.app.help.config

import io.legado.app.constant.AppConst
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isNightMode
import io.legado.app.utils.sysConfiguration
import splitties.init.appCtx

@Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
object AppConfig {
    var userAgent: String = getPrefUserAgent()
    var isEInkMode = appCtx.getPrefString(PreferKey.themeMode) == "3"
    var recordLog = appCtx.getPrefBoolean(PreferKey.recordLog)

    var isNightTheme: Boolean
        get() = sysConfiguration.isNightMode
        set(value) {
        }


    var threadCount: Int
        get() = 16
        set(value) {
        }

    var elevation: Int
        get() = AppConst.sysElevation
        set(value) {
        }

    private fun getPrefUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }


}

