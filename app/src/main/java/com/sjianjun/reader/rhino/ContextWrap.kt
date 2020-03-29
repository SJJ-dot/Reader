package com.sjianjun.reader.rhino

import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

class ContextWrap(val context: Context) {
    val scriptable = context.initSafeStandardObjects()
    fun evaluateString(source: String): Any? {
        return context.evaluateString(
            scriptable, source, null, 0, null
        )
    }

    fun javaToJS(value: Any?): Any? {
        return Context.javaToJS(value, scriptable)
    }

    fun jsToJava(value: Any?, desiredType: Class<*>): Any? {
        return Context.jsToJava(value, desiredType)
    }

    fun putProperty(name: String, value: Any?) {
        ScriptableObject.putProperty(scriptable, name, value)
    }
}
