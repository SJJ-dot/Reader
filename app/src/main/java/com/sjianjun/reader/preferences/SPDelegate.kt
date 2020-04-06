@file:Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")

package com.sjianjun.reader.preferences

import android.content.SharedPreferences
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import sjj.alog.Log
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty

fun <T> getSpValue(
    classifier: KClassifier?,
    key: String,
    def: T,
    sp: SharedPreferences
): T {
    return when (classifier) {
        String::class -> sp.getString(key, def as? String)
        Boolean::class -> sp.getBoolean(key, def as Boolean)
        Float::class -> sp.getFloat(key, def as Float)
        Int::class -> sp.getInt(key, def as Int)
        Long::class -> sp.getLong(key, def as Long)
        Set::class -> sp.getStringSet(key, def as? Set<String>)
        else -> throw IllegalArgumentException("only support String、Boolean、Float、Int、Long、Set<String>")
    } as T
}

fun <T> putSpValue(classifier: KClassifier?, key: String, value: T, sp: SharedPreferences) {
    val edit = sp.edit()
    when (classifier) {
        String::class -> edit.putString(key, value as? String)
        Boolean::class -> edit.putBoolean(key, value as Boolean)
        Float::class -> edit.putFloat(key, value as Float)
        Int::class -> edit.putInt(key, value as Int)
        Long::class -> edit.putLong(key, value as Long)
        Set::class -> edit.putStringSet(key, value as? Set<String>)
        else -> throw IllegalArgumentException("only support String、Boolean、Float、Int、Long、Set<String>")
    } as T
    edit.apply()
}



