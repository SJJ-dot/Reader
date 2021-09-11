package com.sjianjun.reader.preferences

import androidx.core.content.edit
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.tencent.mmkv.MMKV
import sjj.alog.Log

object JsConfig : DelegateSharedPref(MMKV.mmkvWithID("AppConfig_JsConfig")) {
    var localJsVersion by intPref("localJsVersion", 0)

    /**
     * 全部小说源
     */
    var allJsSource by dataPref<List<String>>("allJsSource", emptyList())

    fun saveJs(source: String, js: JavaScript) {
        edit { putString("Js_${source}", gson.toJson(js)) }
        Log.i("保存脚本:${source}")
    }

    fun removeJs(source: String) {
        edit {
            remove("Js_${source}")
            remove("JsVersion_${source}")
        }
        Log.i("删除脚本:${source}")
    }

    fun getJs(source: String): JavaScript? {
        return gson.fromJson<JavaScript>(getString("Js_${source}", null))
    }

    fun saveJsVersion(source: String, version: Int) {
        edit { putInt("JsVersion_${source}", version) }
    }

    fun getJsVersion(source: String): Int {
        return getInt("JsVersion_${source}", 0)
    }
}