package com.sjianjun.reader.preferences

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.App
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.Book

val globalConfig by lazy { AppConfig("default") }
val globalBookConfig by lazy { BookConfig() }

class AppConfig(val name: String) {

    private fun <T> sp(def: T, key: String? = null): DelegateSharedPreferences<T> {
        return DelegateSharedPreferences(
            def,
            key,
            { App.app.getSharedPreferences("AppConfig_$name", Context.MODE_PRIVATE) })
    }

    var javaScriptVersion by sp(0)

    var javaScriptBaseUrl by sp(App.app.getString(R.string.script_base_url))
}

class BookConfig {

    private val config by lazy { App.app.getSharedPreferences("BookConfig", Context.MODE_PRIVATE) }

}