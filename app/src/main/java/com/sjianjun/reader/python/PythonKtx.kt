package com.sjianjun.reader.python

import com.chaquo.python.Python
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import sjj.alog.Log


inline fun <reified T> BookSource.py(func: String, vararg params: String?): T? {
    val py = Python.getInstance()
    val exec = py.getModule("exec_script")
    val param = params.map { "$it" }.reduce { acc, s -> "$acc,$s" }
    val result = exec.callAttr("exec_script", js, func, param).toString()
    if (T::class.java == String::class.java) {
        return result as T
    }
    if (T::class.java == Boolean::class.java) {
        return result.toBoolean() as T
    }
    return gson.fromJson<T>(result)
}