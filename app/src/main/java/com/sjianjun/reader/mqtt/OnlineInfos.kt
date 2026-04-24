package com.sjianjun.reader.mqtt

import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_ONLINE_QUERY_NUM_REQUEST
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_ONLINE_QUERY_NUM_RESP
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
object OnlineInfos {
    val onlineCount = MutableLiveData(0)

    init {
        GlobalScope.launch(Dispatchers.IO) {
            delay(5 * 1000)
            while (true) {
                refresh()
                delay(60 * 1000)//每分钟检查一次
            }
        }
    }

    suspend fun refresh() {
        val payload = mapOf(
            "clientId" to globalConfig.mqttClientId,
            "period" to 10 * 60 //请求30分钟内在线的客户端列表
        )
        val response = MqttUtil.request(TOPIC_ONLINE_QUERY_NUM_REQUEST, TOPIC_ONLINE_QUERY_NUM_RESP, gson.toJson(payload).toByteArray()) ?: return
        val online = gson.fromJson<Map<String, String>>(String(response)) ?: emptyMap()
        onlineCount.postValue(online["online_count"]?.toIntOrNull() ?: 0)
    }
}