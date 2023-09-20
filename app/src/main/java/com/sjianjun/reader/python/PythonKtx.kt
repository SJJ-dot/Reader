package com.sjianjun.reader.python

import com.chaquo.python.Python
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import sjj.alog.Log


inline fun <reified T> BookSource.py(func: String, vararg params: String?): T? {
    val py = Python.getInstance()
    synchronized(py) {
        val exec = py.getModule("exec_script")
        val param = params.map { "\"$it\"" }.reduce { acc, s -> "$acc,$s" }
        val result = exec.callAttr("exec_script", js, func, param)
        return gson.fromJson<T>(result.toString())
    }
}