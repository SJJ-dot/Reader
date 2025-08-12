package com.sjianjun.reader.utils

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebSettings
import androidx.annotation.OptIn
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

@OptIn(WebSettingsCompat.ExperimentalBackForwardCache::class)
fun WebSettings.setBackForwardCacheEnabled() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
        WebSettingsCompat.setBackForwardCacheEnabled(this, true);
    }
}


fun WebSettings.init(headerMap: Map<String, String>? = null){
    setDarkening()
    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    domStorageEnabled = true
    allowContentAccess = true
    useWideViewPort = true
    loadWithOverviewMode = true
    javaScriptEnabled = true
    setSupportZoom(true)
    builtInZoomControls = true
    displayZoomControls = false
    headerMap?.get("User-Agent")?.let {
        userAgentString = it
    }
    blockNetworkImage = false // 设置图片加载方式，默认true，表示不加载图片
    loadsImagesAutomatically = true // 支持自动加载图片
    setBackForwardCacheEnabled()
}