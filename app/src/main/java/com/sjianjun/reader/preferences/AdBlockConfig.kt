package com.sjianjun.reader.preferences

import androidx.core.content.edit
import com.sjianjun.reader.view.CustomWebView
import com.tencent.mmkv.MMKV


object AdBlockConfig : DelegateSharedPref(MMKV.mmkvWithID("AppConfig_AdBlockConfig")) {
    /**
     * 需要拦截的广告SDK url
     */
    var adBlockList by dataPref<List<CustomWebView.AdBlock>>("adBlockList", emptyList())


    var adBlockFilterUrlVersion by intPref("adBlockFilterUrlVersion", 0)


    fun saveAdBlockJs(source: String, js: String) {
        edit { putString("AdBlockJs_${source}", js) }
    }

    fun getAdBlockJs(source: String): String {
        return getString("AdBlockJs_${source}", "")!!
    }

    fun saveAdBlockJsVersion(source: String, version: Int) {
        edit { putInt("AdBlockJsVersion_${source}", version) }
    }

    fun getAdBlockJsVersion(source: String): Int {
        return getInt("AdBlockJsVersion_${source}", 0)
    }


}