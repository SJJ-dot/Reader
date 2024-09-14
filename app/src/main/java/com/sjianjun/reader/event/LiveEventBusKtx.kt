package com.sjianjun.reader.event

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.jeremyliao.liveeventbus.LiveEventBus
import com.sjianjun.reader.BaseActivity


object EventBus {
    @JvmStatic
    fun post(value: String) {
        post(value, value)
    }

    @JvmStatic
    fun post(value: Any) {
        post(value.javaClass.name, value)
    }

    @JvmStatic
    fun post(key: String, value: Any) {
        LiveEventBus.get<Any>(key).post(value)
    }


    @JvmStatic
    fun <T> observe(clazz: Class<T>, owner: LifecycleOwner, observer: Observer<T>) {
        observe(clazz.name, owner, observer)
    }

    @JvmStatic
    fun <T> observe(key: String, owner: LifecycleOwner, observer: Observer<T>) {
        LiveEventBus.get<T>(key).observe(owner, observer)
    }

    @JvmStatic
    fun <T> observeForever(key: String, observer: Observer<T>) {
        LiveEventBus.get<T>(key).observeForever(observer)
    }
}

fun <T> BaseActivity.observe(key: String, observer: Observer<T>) {
    EventBus.observe(key, this, observer)
}

inline fun <reified T> BaseActivity.observe(observer: Observer<T>) {
    EventBus.observe(T::class.java, this, observer)
}
