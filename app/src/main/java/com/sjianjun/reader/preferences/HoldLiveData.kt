package com.sjianjun.reader.preferences

import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import sjj.alog.Log
import java.lang.Exception
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty


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
            val classifier = property.returnType.arguments[0].type!!.classifier
            val key = k ?: property.name
            val sp = sharedPreferences ?: return MutableLiveData(def)
            liveData = HoldLiveData(def, k ?: property.name, classifier, sp)
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
    private val classifier: KClassifier?,
    private val sp: SharedPreferences
) : MutableLiveData<T>(getSpValue(classifier, key, def, sp)) {


    /**
     * 保存数据到 SharedPreferences 中
     */
    override fun setValue(value: T) {
        putSpValue(classifier, key, value, sp)
        super.setValue(value)
    }

}