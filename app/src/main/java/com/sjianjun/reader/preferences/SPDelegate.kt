@file:Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")

package com.sjianjun.reader.preferences

import android.content.SharedPreferences
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import sjj.alog.Log
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty

private fun <T> getSpValue(
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

private fun <T> putSpValue(classifier: KClassifier?, key: String, value: T, sp: SharedPreferences) {
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
        return getSpValue(property.returnType.classifier, key, def, sp)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val key: String = k ?: property.name
        putSpValue(property.returnType.classifier, key, value, sp() ?: return)
    }
}


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
            val value = getSpValue(classifier, key, def, sp)
            liveData = HoldLiveData(value, k ?: property.name, classifier, sp)
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
) : MutableLiveData<T>(def) {


    /**
     * 保存数据到 SharedPreferences 中
     */
    private fun saveValue(value: T) {
        putSpValue(classifier, key, value, sp)
        //通知 live data 更新
        super.postValue(value)
    }


    override fun postValue(value: T) {
        saveValue(value)
    }

    override fun setValue(value: T) {
        saveValue(value)
    }

}