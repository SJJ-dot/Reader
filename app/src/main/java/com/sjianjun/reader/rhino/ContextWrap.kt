package com.sjianjun.reader.rhino

import org.mozilla.javascript.Context
import org.mozilla.javascript.ImporterTopLevel
import org.mozilla.javascript.ScriptableObject

class ContextWrap(val context: Context) {
    val scriptable = ImporterTopLevel(context)
    fun eval(source: String): Any? {
        return context.evaluateString(
            scriptable, source, null, 0, null
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
