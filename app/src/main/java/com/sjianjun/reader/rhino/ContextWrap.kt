package com.sjianjun.reader.rhino

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.SearchResult
import com.sjianjun.reader.http.CookieMgr
import com.sjianjun.reader.http.http
import com.sjianjun.reader.utils.AesUtil
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import org.mozilla.javascript.Context
import org.mozilla.javascript.ImporterTopLevel
import org.mozilla.javascript.ScriptableObject
import sjj.alog.Log

class ContextWrap(val context: Context) {
    val scriptable = ImporterTopLevel(context)
    fun eval(source: String, sourceName: String? = null): Any? {
        return context.evaluateString(
            scriptable, source, sourceName, 0, null
        )
    }

    fun get(any: Any?): Any? {
        return scriptable.get(any)
    }

    fun javaToJS(value: Any?): Any? {
        return Context.javaToJS(value, scriptable)
    }

    inline fun <reified T> jsToJava(value: Any?): T? {
        return jsToJava(value, T::class.java) as T?
    }

    fun jsToJava(value: Any?, desiredType: Class<*>): Any? {
        return Context.jsToJava(value, desiredType)
    }

    fun putProperty(name: String, value: Any?) {
        ScriptableObject.putProperty(scriptable, name, value)
    }
}

