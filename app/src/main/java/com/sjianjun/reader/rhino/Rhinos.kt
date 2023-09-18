package com.sjianjun.reader.rhino

import org.mozilla.javascript.Context

inline fun <reified T> jsCtx(runner: ContextWrap.() -> T): T? {
    val context = Context.enter()
    return try {
        context.optimizationLevel = -1
        val wrap = ContextWrap(context)
        wrap.runner()
    } finally {
        Context.exit()
    }
}


inline fun <reified T> importClassCode(): String {
    return "importClass(Packages.${T::class.java.name})"
}

fun importPackageCode(pkg: String): String {
    return "importClass(Packages.${pkg})"
}

