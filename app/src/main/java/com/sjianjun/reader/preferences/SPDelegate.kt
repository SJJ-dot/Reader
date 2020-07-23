@file:Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")

package com.sjianjun.reader.preferences

import android.content.SharedPreferences
import sjj.novel.util.gson
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

fun <T> getSpValue(
    kType: KType,
    key: String,
    def: T,
    sp: SharedPreferences
): T {
    return when (kType.classifier) {
        String::class -> sp.getString(key, def as? String)
        Boolean::class -> sp.getBoolean(key, def as Boolean)
        Float::class -> sp.getFloat(key, def as Float)
        Int::class -> sp.getInt(key, def as Int)
        Long::class -> sp.getLong(key, def as Long)
        Set::class -> sp.getStringSet(key, def as? Set<String>)
        else -> {
            val str = sp.getString(key, null)
            if (str != null) {
                gson.fromJson(str, kType.javaType)
            } else {
                def
            }

        }
    } as T
}

fun <T> putSpValue(kType: KType, key: String, value: T, sp: SharedPreferences) {
    val edit = sp.edit()
    when (kType.classifier) {
        String::class -> edit.putString(key, value as? String)
        Boolean::class -> edit.putBoolean(key, value as Boolean)
        Float::class -> edit.putFloat(key, value as Float)
        Int::class -> edit.putInt(key, value as Int)
        Long::class -> edit.putLong(key, value as Long)
        Set::class -> edit.putStringSet(key, value as? Set<String>)
        else -> {
            edit.putString(key, gson.toJson(value))
        }
    } as T
    edit.apply()
}




