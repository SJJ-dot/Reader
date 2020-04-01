package com.sjianjun.reader.rhino

import org.mozilla.javascript.Context
import org.mozilla.javascript.ImporterTopLevel

inline fun <reified T> js(runner: ContextWrap.() -> T): T {
    val context = Context.enter()
    try {
        context.optimizationLevel = -1
        val wrap = ContextWrap(context)
        return wrap.runner()
    } finally {
        Context.exit()
    }
}

inline fun <reified T> importClassCode(): String {
    return "importClass(Packages.${T::class.java.name})"
}
fun importPackageCode(pkg:String): String {
    return "importClass(Packages.${pkg})"
}

