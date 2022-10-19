package com.sjianjun.reader.preferences

import androidx.core.content.edit
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.tencent.mmkv.MMKV
import sjj.alog.Log
import java.util.concurrent.ConcurrentHashMap

object JsConfig : DelegateSharedPref(MMKV.mmkvWithID("AppConfig_JsConfig2")) {


    private val allJs: MutableMap<String, BookSource?> = ConcurrentHashMap()

    fun saveJs(vararg sources: BookSource) {
        edit {
            sources.forEach { js ->
                putString("Js_${js.source}", gson.toJson(js))
                allJs[js.source] = js
            }
        }
        Log.i("保存脚本:${sources.map { it.source }}")
    }

    fun removeJs(vararg sources: BookSource) {
        if (sources.isEmpty()) {
            return
        }
        edit {
            sources.forEach {
                allJs.remove(it.source)
                remove("Js_${it.source}")
            }
        }

        Log.i("删除脚本:${sources.map { it.source }}")
    }

    fun getJs(source: String): BookSource? {
        if (allJs.containsKey(source)) {
            return allJs[source]
        }
        val script = try {
            gson.fromJson<BookSource>(getString("Js_${source}", null))
        } catch (e: Exception) {
            Log.e("书源加载失败")
            null
        } ?: return null
        allJs[source] = script
        return script
    }

    fun getAllJs(): List<BookSource> {
        val mmkv = pref as MMKV
        val source2 = mmkv.allKeys()?.toMutableSet()?.toMutableSet() ?:return emptyList()

        allJs.forEach { (t, _) ->
            source2.remove(t)
        }

        source2.forEach {
            if (it.startsWith("Js_")) {
                getJs(it.replace("Js_",""))
            }
        }
        return allJs.values.mapNotNull { it }.sortedBy { it.source }
    }

}