package com.sjianjun.reader.preferences

import androidx.core.content.edit
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.tencent.mmkv.MMKV
import sjj.alog.Log
import java.util.concurrent.ConcurrentHashMap

object JsConfig : DelegateSharedPref(MMKV.mmkvWithID("AppConfig_JsConfig2")) {
    var localJsVersion by intPref("localJsVersion", 0)
    var localAdblockVersion by intPref("localAdblockVersion", 0)

    /**
     * 全部小说源
     */
    var allJsSource: MutableSet<String> = ConcurrentHashMap.newKeySet()
        private set(value) {
            if (value !== field) {
                field.clear()
                field.addAll(value)
            }
            edit { putStringSet("allJsSource", field) }
        }
        get() {
            if (field.isEmpty()) {
                field.addAll(getStringSet("allJsSource", emptySet())!!)
            }
            return field
        }

    private val allJs: MutableMap<String, BookSource?> = ConcurrentHashMap()

    fun saveJs(vararg js: BookSource) {
        edit {
            js.forEach { js ->
                putString("Js_${js.source}", gson.toJson(js))
            }
        }
        js.forEach { js ->
            if (!allJsSource.contains(js.source)) {
                allJsSource.add(js.source)
            }
            allJs[js.source] = js
        }
        allJsSource = allJsSource


        Log.i("保存脚本:${js.map { it.source }}")
    }

    fun removeJs(vararg sources: String) {
        if (sources.isEmpty()) {
            return
        }
        sources.forEach {
            allJsSource.remove(it)
            allJs.remove(it)
        }
        allJsSource = allJsSource

        edit {
            sources.forEach {
                remove("Js_${it}")
            }
        }

        Log.i("删除脚本:${sources.toList()}")
    }

    fun getJs(source: String): BookSource? {
        if (allJs.containsKey(source)) {
            return allJs[source]
        }
        val script = gson.fromJson<BookSource>(getString("Js_${source}", null)) ?: return null
        allJs[source] = script
        return script
    }

    fun getAllJs(): List<BookSource> {
        val source2 = allJsSource.toMutableSet()
        allJs.forEach { (t, _) ->
            source2.remove(t)
        }
        source2.forEach {
            getJs(it)
        }
        return allJs.values.mapNotNull { it }
    }

}