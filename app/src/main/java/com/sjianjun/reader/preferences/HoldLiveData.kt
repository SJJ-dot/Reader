package com.sjianjun.reader.preferences

import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import kotlin.reflect.KProperty
import kotlin.reflect.KType


/**
 * only support String、Boolean、Float、Double、Int、Long、Set<String>、ByteArray
 * mmkv Set<String> 不允许为空
 */
class DelegateLiveData<T>(
    private val def: T,
    private val k: String? = null,
    val sp: () -> SharedPreferences?
) {
    private var liveData: MutableLiveData<T>? = null
    private val sharedPreferences by lazy { sp() }

    @Synchronized
    operator fun getValue(thisRef: Any?, property: KProperty<*>): MutableLiveData<T> {
        if (liveData == null) {
            val kType = property.returnType.arguments[0].type!!
            val key = k ?: property.name
            val sp = sharedPreferences ?: return MutableLiveData(def)
            liveData = HoldLiveData(def, key, kType, sp)
        }
        return liveData!!
    }

}

/**
 *
 * only support String、Boolean、Float、Double、Int、Long、Set<String>、ByteArray
 */
@Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
class HoldLiveData<T>(
    private val def: T,
    private val key: String,
    private val kType: KType,
    private val sp: SharedPreferences
) : MutableLiveData<T>(getSpValue(kType, key, def, sp)) {


    /**
     * 保存数据到 SharedPreferences 中
     */
    override fun setValue(value: T) {
        putSpValue(kType, key, value, sp)
        super.setValue(value)
    }

}