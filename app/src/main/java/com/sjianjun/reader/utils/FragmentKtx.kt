package com.sjianjun.reader.utils

import androidx.fragment.app.Fragment

inline fun <reified T : Fragment> fragmentCreate(k: String, v: Any): T {
    return fragmentCreate<T>(k to v)
}

inline fun <reified T : Fragment> fragmentCreate(vararg param: Pair<String, Any>): T {
    val fragment = T::class.java.newInstance()
    if (param.isNotEmpty()) {
        fragment.arguments = bundle(*param)
    }
    return fragment
}