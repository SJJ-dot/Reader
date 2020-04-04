package com.sjianjun.reader.utils

import android.os.Bundle

fun bundle(k: String, v: Any?): Bundle {
    return bundle(k to v)
}

fun bundle(vararg params:Pair<String,Any?>): Bundle {
    val bundle = Bundle()
    for (param in params) {
        bundle.putString(param.first,param.second.toString())
    }
    return bundle
}

fun Bundle.put(k: String, v: String) {
    return putString(k,v)
}