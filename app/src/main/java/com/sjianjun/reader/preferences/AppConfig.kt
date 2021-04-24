package com.sjianjun.reader.preferences

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.App
import com.sjianjun.reader.utils.BOOK_SOURCE_QI_DIAN
import com.sjianjun.reader.view.CustomWebView

val globalConfig by lazy { AppConfig("default") }

class AppConfig(val name: String) {
    private fun getSharedPreferences(): SharedPreferences {
        return App.app.getSharedPreferences("AppConfig_$name", Context.MODE_PRIVATE)
    }

    private fun <T> sp(def: T, key: String? = null): DelegateSharedPreferences<T> {
        return DelegateSharedPreferences(def, key, { getSharedPreferences() })
    }

    private fun <T> liveDataMap(defValue: T, vararg keys: String): LiveDataMapImpl<T> {
        return LiveDataMapImpl(defValue, { getSharedPreferences() }, *keys)
    }

    var javaScriptVersion by DelegateSharedPreferences(0) { getSharedPreferences() }

    /**
     * github 发布的版本信息
     */
    var releasesInfo by DelegateSharedPreferences("") { getSharedPreferences() }

    /**
     * 上次检查更新的时间
     */
    var lastCheckUpdateTime by DelegateSharedPreferences(0L) { getSharedPreferences() }

    var appDayNightMode by DelegateSharedPreferences(MODE_NIGHT_NO) { getSharedPreferences() }

    val bookCityDefaultSource by DelegateLiveData(BOOK_SOURCE_QI_DIAN) { getSharedPreferences() }

    val qqAuthLoginUri = MutableLiveData<Uri>()

    var adBlockUrlListVersion by DelegateSharedPreferences(0) { getSharedPreferences() }

    /**
     * 需要拦截的广告SDK url
     */
    var adBlockList by DelegateSharedPreferences(emptyList<CustomWebView.AdBlock>()) { getSharedPreferences() }

    /**
     * 阅读器亮度蒙层的颜色
     */
    val readerBrightnessMaskColor by DelegateLiveData(0) { getSharedPreferences() }

    /**
     * 阅读器 内容字体大小 、章节名称+4
     */
    val readerFontSize by DelegateLiveData(22) { getSharedPreferences() }

    /**
     * 阅读器 内容字体行间距
     */
    val readerLineSpacing by DelegateLiveData(1.5f) { getSharedPreferences() }

    /**
     * 阅读器 页面样式 位置索引
     */
    val readerPageStyle by DelegateLiveData(0) { getSharedPreferences() }

    /**
     * 上一次使用的深色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastDarkTheme by DelegateLiveData(0) { getSharedPreferences() }

    /**
     * 上一次使用的浅色 颜色样式 用于白天夜间切换 样式0支持白天和夜间模式
     */
    val lastLightTheme by DelegateLiveData(0) { getSharedPreferences() }

    /**
     * 记录上一次启动时App版本
     */
    var lastAppVersion by DelegateSharedPreferences(0) { getSharedPreferences() }
    var lastAppVersionName by DelegateSharedPreferences("") { getSharedPreferences() }
}

