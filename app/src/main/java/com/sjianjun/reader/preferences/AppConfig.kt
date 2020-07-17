package com.sjianjun.reader.preferences

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.App
import com.sjianjun.reader.utils.JS_SOURCE_QI_DIAN

val globalConfig by lazy { AppConfig("default") }
val globalBookConfig by lazy { BookConfig() }

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

    val bookCityDefaultSource by DelegateLiveData(JS_SOURCE_QI_DIAN) { getSharedPreferences() }

    val qqAuthLoginUri = MutableLiveData<Uri>()

    var adBlockUrlListVersion by DelegateSharedPreferences(0) { getSharedPreferences() }

    /**
     * 需要拦截的广告SDK url
     */
    var adBlockUrlList by DelegateSharedPreferences(emptyList<String>()) { getSharedPreferences() }

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
}

class BookConfig {

    private val config by lazy { App.app.getSharedPreferences("BookConfig", Context.MODE_PRIVATE) }

}