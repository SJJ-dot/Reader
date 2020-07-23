package com.sjianjun.reader.preferences

import android.content.SharedPreferences
import kotlin.reflect.KProperty

@Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
class DelegateSharedPreferences<T>(
    private val def: T,
    private val k: String? = null,
    val sp: () -> SharedPreferences?
) {

    private val sharedPreferences by lazy { sp() }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val key: String = k ?: property.name
        val sp = sharedPreferences ?: return def
        return getSpValue(property.returnType, key, def, sp)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val key: String = k ?: property.name
        putSpValue(property.returnType, key, value, sp() ?: return)
    }
}
