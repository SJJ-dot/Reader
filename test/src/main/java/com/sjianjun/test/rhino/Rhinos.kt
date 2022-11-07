package com.sjianjun.test.rhino

import org.mozilla.javascript.Context

inline fun <reified T> js(runner: ContextWrap.() -> T): T? {
    val context = Context.enter()
    return try {
        context.optimizationLevel = -1
        val wrap = ContextWrap(context)
        wrap.runner()
    } finally {
        Context.exit()
    }
}

fun <T> runJs(runner: ContextWrap.() -> T): T? {
    return js<Any?>(runner) as T?
}


inline fun <reified T> importClassCode(): String {
    return "importClass(Packages.${T::class.java.name})"
}

fun importPackageCode(pkg: String): String {
    return "importClass(Packages.${pkg})"
}

