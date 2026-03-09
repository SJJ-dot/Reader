package com.sjianjun.reader.mqtt

import androidx.lifecycle.MutableLiveData
import java.util.concurrent.ConcurrentHashMap

object OnlineInfos {
    val onlineMap = MutableLiveData(ConcurrentHashMap<String, Long>())

    fun refresh() {
        val now = System.currentTimeMillis()
        val iterator = onlineMap.value?.iterator()
        var changed = false
        if (iterator != null) {
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > 60 * 1000) {
                    iterator.remove()
                    changed = true
                }
            }
        }
        if (changed) {
            onlineMap.postValue(onlineMap.value)
        }
    }

    fun parseInfo(topic: String, time: Long) {
        val id = topic.substringAfterLast("/")
        onlineMap.value?.put(id, time)
        onlineMap.postValue(onlineMap.value)
        refresh()
    }

    override fun toString(): String {
        return "OnlineInfos(onlines=${onlineMap.value})"
    }
}