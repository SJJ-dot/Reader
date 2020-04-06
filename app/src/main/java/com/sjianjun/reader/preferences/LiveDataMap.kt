package com.sjianjun.reader.preferences

import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty

class LiveDataMapImpl<T>(
    private val defValue: T,
    sharedPreferences: () -> SharedPreferences,
    private vararg val keys: String
) : LiveDataMap<T> {

    private val sharedPreferences by lazy { sharedPreferences() }
    private lateinit var classifier: KClassifier
    @Synchronized
    operator fun getValue(thisRef: Any?, property: KProperty<*>): LiveDataMap<T> {
        classifier = property.returnType.arguments[0].type!!.classifier!!
        return this
    }


    private val map = mutableMapOf<String, MutableLiveData<T>>()

    private fun key(vararg value: String): String {
        return value.mapIndexed { index, s ->
            "${keys.getOrNull(index)}:$s"
        }.reduce { acc, s -> "$acc $s" }
    }

    override fun getValue(vararg params: String): MutableLiveData<T> {
        val key = key(*params)
        return map.getOrPut(key) {
            HoldLiveData(defValue, key, classifier, sharedPreferences)
        }
    }


}

interface LiveDataMap<T> {
    fun getValue(vararg params: String): MutableLiveData<T>
}

fun <T> liveDataMap(
    defValue: T,
    sharedPreferences: () -> SharedPreferences,
    vararg keys: String
): LiveDataMapImpl<T> {
    return LiveDataMapImpl(defValue, sharedPreferences, *keys)
}