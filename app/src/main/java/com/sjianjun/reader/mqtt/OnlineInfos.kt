package com.sjianjun.reader.mqtt

import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_ONLINE_QUERY_NUM_REQUEST
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_ONLINE_QUERY_NUM_RESP
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import kotlinx.coroutines.DelicateCoroutinesApi

@OptIn(DelicateCoroutinesApi::class)
object OnlineInfos {
    val onlineCount = MutableLiveData(0)

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