package com.sjianjun.reader.utils

import androidx.fragment.app.Fragment

inline fun <reified T : Fragment> create(k: String, v: Any): T {
    return create<T>(k to v)
}

inline fun <reified T : Fragment> create(vararg param: Pair<String, Any>): T {
    val fragment = T::class.java.newInstance()
    if (param.isNotEmpty()) {
        fragment.arguments = bundle(*param)
    }
    return fragment
}