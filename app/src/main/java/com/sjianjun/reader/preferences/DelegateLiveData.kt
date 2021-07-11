package com.sjianjun.reader.preferences

import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData

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