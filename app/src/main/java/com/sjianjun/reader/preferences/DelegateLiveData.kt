package com.sjianjun.reader.preferences

import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import com.google.gson.reflect.TypeToken
import com.sjianjun.reader.utils.gson

inline fun <reified T> SharedPreferences.dataLivedata(
    key: String,
    def: T
): DataLivedata<T> {
    val jsonStr = getString(key, null)
    if (jsonStr != null) {
        val type = object : TypeToken<T>() {}.type
        return DataLivedata(this, key, gson.fromJson(jsonStr, type))
    }
    return DataLivedata<T>(this, key, def)
}

class DataLivedata<T>(private val pref: SharedPreferences, private val key: String, def: T) :
    MutableLiveData<T>(def) {

    override fun setValue(value: T) {
        super.setValue(value)
        pref.edit().putString(key, gson.toJson(value)).apply()
    }
}

fun SharedPreferences.strLivedata(
    key: String,
    def: String?
): StrLivedata {
    return StrLivedata(this, key, def)
}

class StrLivedata(private val pref: SharedPreferences, private val key: String, def: String?) :
    MutableLiveData<String>(pref.getString(key, def)) {

    override fun setValue(value: String?) {
        super.setValue(value)
        pref.edit().putString(key, value).apply()
    }
}

fun SharedPreferences.intLivedata(
    key: String,
    def: Int = 0
): IntLivedata {
    return IntLivedata(this, key, def)
}

class IntLivedata(private val pref: SharedPreferences, private val key: String, def: Int = 0) :
    MutableLiveData<Int>(pref.getInt(key, def)) {

    override fun setValue(value: Int) {
        super.setValue(value)
        pref.edit().putInt(key, value).apply()
    }
}

fun SharedPreferences.floatLivedata(
    key: String,
    def: Float = 0f
): FloatLivedata {
    return FloatLivedata(this, key, def)
}

class FloatLivedata(private val pref: SharedPreferences, private val key: String, def: Float = 0f) :
    MutableLiveData<Float>(pref.getFloat(key, def)) {

    override fun setValue(value: Float) {
        super.setValue(value)
        pref.edit().putFloat(key, value).apply()
    }
}