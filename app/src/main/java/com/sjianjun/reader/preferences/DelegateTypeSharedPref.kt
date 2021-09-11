package com.sjianjun.reader.preferences

import android.content.SharedPreferences
import com.sjianjun.reader.utils.gson
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KProperty

fun SharedPreferences.intPref(
    key: String,
    def: Int = 0
): IntPref {
    return IntPref(this, key, def)
}

class IntPref(
    private val pref: SharedPreferences,
    private val key: String,
    val def: Int = 0
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
        return pref.getInt(key, def)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        pref.edit().putInt(key, value).apply()
    }
}

fun SharedPreferences.strPref(
    key: String,
    def: String? = null
): StrPref {
    return StrPref(this, key, def)
}

class StrPref(
    private val pref: SharedPreferences,
    private val key: String,
    val def: String? = null
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String? {
        return pref.getString(key, def)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
        pref.edit().putString(key, value).apply()
    }
}

fun SharedPreferences.longPref(
    key: String,
    def: Long = 0
): LongPref {
    return LongPref(this, key, def)
}

class LongPref(
    private val pref: SharedPreferences,
    private val key: String,
    val def: Long = 0
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Long {
        return pref.getLong(key, def)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
        pref.edit().putLong(key, value).apply()
    }
}

fun SharedPreferences.floatPref(
    key: String,
    def: Float = 0f
): FloatPref {
    return FloatPref(this, key, def)
}

class FloatPref(
    private val pref: SharedPreferences,
    private val key: String,
    val def: Float = 0f
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Float {
        return pref.getFloat(key, def)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
        pref.edit().putFloat(key, value).apply()
    }
}

inline fun <reified T> SharedPreferences.dataPref(
    key: String,
    def: T
): DataPref<T> {
    return object : DataPref<T>(this, key, def) {}
}

abstract class DataPref<T>(
    private val pref: SharedPreferences,
    private val key: String,
    val def: T
) {
    var cache: T? = null
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val lCache = cache
        if (lCache != null) {
            return lCache
        }
        val json = pref.getString(key, null)
        if (json == null) {
            cache = def
            return def
        }
        val superclass = this.javaClass.genericSuperclass as ParameterizedType
        val r = gson.fromJson<T>(json, superclass.actualTypeArguments[0])
        cache = r
        return r
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        cache = value
        pref.edit().putString(key, gson.toJson(value)).apply()
    }
}