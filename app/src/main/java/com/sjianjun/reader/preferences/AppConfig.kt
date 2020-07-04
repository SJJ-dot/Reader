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

    var javaScriptVersion by sp(0)

    /**
     * github 发布的版本信息
     */
    var releasesInfo by sp("")

    /**
     * 上次检查更新的时间
     */
    var lastCheckUpdateTime by sp(0L)

    var appDayNightMode by sp(MODE_NIGHT_NO)

    var bookCityDefaultSource by sp(JS_SOURCE_QI_DIAN)

    val qqAuthLoginUri = MutableLiveData<Uri>()

    var adBlockUrlSetVersion by sp(0)
    /**
     * 需要拦截的广告SDK url
     */
    var adBlockUrlSet by sp(emptySet<String>())
}

class BookConfig {

    private val config by lazy { App.app.getSharedPreferences("BookConfig", Context.MODE_PRIVATE) }

}