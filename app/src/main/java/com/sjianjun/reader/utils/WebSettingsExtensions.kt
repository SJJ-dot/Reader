package com.sjianjun.reader.utils

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatDelegate
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.sjianjun.reader.preferences.globalConfig
import sjj.alog.Log

/**
 * 设置是否夜间模式
 */
@SuppressLint("RequiresFeature")
fun WebSettings.setDarkening() {
    val isNight = globalConfig.appDayNightMode == AppCompatDelegate.MODE_NIGHT_YES
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        kotlin.runCatching {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, isNight)
            return
        }.onFailure {
            Log.e("WebSettingsExtensions setAlgorithmicDarkeningAllowed failed", it)
        }
    }

    if (isNight) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDarkStrategy(
                this,
                WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
            )
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDark(
                this,
                WebSettingsCompat.FORCE_DARK_ON
            )
        }
    }
}