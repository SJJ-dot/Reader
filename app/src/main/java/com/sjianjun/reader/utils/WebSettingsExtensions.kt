package com.sjianjun.reader.utils

import android.annotation.SuppressLint
import android.os.Build
import android.view.View.SCROLLBARS_INSIDE_OVERLAY
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
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
    // 禁用 BFCache：反复前进/后退后缓存状态可能损坏导致页面渲染空白
    // 前进/后退历史仍然保留，只是改为重新加载页面而非从缓存恢复
    if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
        WebSettingsCompat.setBackForwardCacheEnabled(this, true)
    }
}

private var first = true
fun WebView.init(headerMap: Map<String, String>? = null) {
    if (first) {
        first = false
        clearCache(true)
    }
    val cookieManager = CookieManager.getInstance();
    cookieManager.setAcceptCookie(true) // 启用 Cookie 支持
    cookieManager.setAcceptThirdPartyCookies(this, true) // 启用第三方 Cookie
    scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
    settings.apply {
        textZoom = globalConfig.webFontZoomSize
        setDarkening()
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        domStorageEnabled = true
        allowContentAccess = true
        allowFileAccess = false // 禁止访问文件系统，安全最佳实践
        useWideViewPort = true
        loadWithOverviewMode = true
        javaScriptEnabled = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        setSupportMultipleWindows(false) // 支持 window.open()
        headerMap?.get("User-Agent")?.let {
            userAgentString = it
        }
        blockNetworkImage = false // 设置图片加载方式，默认true，表示不加载图片
        loadsImagesAutomatically = true // 支持自动加载图片
        setBackForwardCacheEnabled()
    }
}
