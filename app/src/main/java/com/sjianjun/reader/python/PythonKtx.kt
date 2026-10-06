package com.sjianjun.reader.python

import com.chaquo.python.Python
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import sjj.alog.Log


@PublishedApi internal val execModule by lazy { Python.getInstance().getModule("exec_script") }


/**
 * 调用Python书源脚本
 * @param param2 第二个参数，仅 getDetails 使用（书籍最新章节链接，没有时传空串）
 */
inline fun <reified T> BookSource.py(func: String, param: String?, param2: String? = null): T? {
    val exec = execModule
    val pyResult = when {
        !param.isNullOrBlank() && param2 != null -> exec.callAttr("exec_script", js, func, param, param2)
        param.isNullOrBlank() -> exec.callAttr("exec_script", js, func)
        else -> exec.callAttr("exec_script", js, func, param)
    }
    return when (T::class.java) {
        String::class.java -> pyResult.toString() as T
        Int::class.java -> pyResult.toInt() as T
        Boolean::class.java -> pyResult.toString().toBoolean() as T
        else -> {
            val json = pyResult.toString()
            try {
                gson.fromJson<T>(json)
            } catch (e: Exception) {
                Log.i("调用脚本方法：$func, 参数：$param, 返回结果：$json")
                throw e
            }
        }
    }
}