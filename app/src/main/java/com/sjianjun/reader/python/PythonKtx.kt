package com.sjianjun.reader.python

import com.chaquo.python.Python
import com.sjianjun.reader.bean.BookSource
import sjj.alog.Log


inline fun <reified T> BookSource.py(name: String, vararg params: String?): T? {
    Log.e("AAA==>>>>>>>>>")
    val py = Python.getInstance()
    val imp = py.getModule("imp")
    val scriptModule = imp.callAttr("new_module", "script_module")
    py.builtins.callAttr("exec", js, scriptModule["__dict__"])
    val result = scriptModule.callAttr(name, *params)
    Log.e(result)
    Log.e("AAA==<<<<<<<<")
    return null
}