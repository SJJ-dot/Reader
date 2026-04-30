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
    val onlineInfo = MutableLiveData<OnlineInfo>()

    suspend fun refresh() {
        val payload = mapOf(
            "clientId" to globalConfig.mqttClientId,
            "period" to 10 * 60 //请求30分钟内在线的客户端列表
        )
        val response = MqttUtil.request(TOPIC_ONLINE_QUERY_NUM_REQUEST, TOPIC_ONLINE_QUERY_NUM_RESP, gson.toJson(payload).toByteArray()) ?: return
        val online = gson.fromJson<OnlineInfo>(String(response))?:return
        onlineInfo.postValue(online)
    }
}

/**
 * {"client_id": "c412c12669e74d1d91b871355a97faf1",
 * "user_id": "c412c12669e74d1d91b871355a97faf1",
 * "online_count": 2,
 * "today_online_seconds": 115,
 * "total_online_seconds": 115,
 * "level":
 * {"major": "炼气",
 * "stage": 1,
 * "stage_name":
 * "炼气1层",
 * "level_index": 1,
 * "progress": 0.0319,
 * "current_stage_required_seconds": 3600,
 * "next_level_need_seconds": 3485,
 * "is_max_level": false},
 * "server_ts": 1777564567}
 */
class OnlineInfo{
    var client_id: String = ""
    var online_count: Int = 0
    var today_online_seconds: Int = 0
    var total_online_seconds: Int = 0
    var level: LevelInfo? = null

    class LevelInfo {
        var major: String = ""
        var stage: Int = 0
        var stage_name: String = ""
        var level_index: Int = 0
        var progress: Double = 0.0
        var current_stage_required_seconds: Int = 0
        var next_level_need_seconds: Int = 0
        var is_max_level: Boolean = false
        var server_ts: Long = 0
    }
}