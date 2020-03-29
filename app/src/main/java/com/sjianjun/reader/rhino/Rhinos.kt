package com.sjianjun.reader.rhino

import org.mozilla.javascript.Context

inline fun js(runner: ContextWrap.() -> Unit) {
    val context = Context.enter()
    try {
        context.optimizationLevel = -1
        val wrap = ContextWrap(context)
        wrap.runner()
    } finally {
        Context.exit()
    }
}


