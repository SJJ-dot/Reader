package com.sjianjun.reader.utils

import android.os.Bundle
import android.os.Parcelable

fun bundle(k: String, v: Any?): Bundle {
    return bundle(k to v)
}

fun bundle(vararg params: Pair<String, Any?>): Bundle {
    val bundle = Bundle()
    for (param in params) {
        val value = param.second
        if (value is Parcelable) {
            bundle.putParcelable(param.first, value)
        } else {
            bundle.putString(param.first, value.toString())
        }
    }
    return bundle
}