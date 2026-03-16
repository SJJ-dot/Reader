package com.sjianjun.reader.mqtt

import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@OptIn(DelicateCoroutinesApi::class)
object OnlineInfos {
    val onlineMap = MutableLiveData(ConcurrentHashMap<String, Long>())

    init {
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                val changed = refresh()
                if (changed) {
                    onlineMap.postValue(onlineMap.value)
                }
                delay(60 * 1000)//每分钟检查一次
            }
        }
    }

    fun refresh(): Boolean {
        val now = System.currentTimeMillis()
        val iterator = onlineMap.value?.iterator()
        var changed = false
        if (iterator != null) {
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > 30 * 60 * 1000) {//30分钟未更新则认为离线
                    iterator.remove()
                    changed = true
                }
            }
        }
        return changed
    }

    fun parseInfo(topic: String, time: Long) {
        val id = topic.substringAfterLast("/")
        if (onlineMap.value?.put(id, time) == null && globalConfig.mqttClientId != id) {
            toast("书友上线了")
        }
        refresh()
        onlineMap.postValue(onlineMap.value)
    }

    override fun toString(): String {
        return "OnlineInfos(onlines=${onlineMap.value})"
    }
}