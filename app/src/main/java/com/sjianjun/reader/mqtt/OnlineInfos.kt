package com.sjianjun.reader.mqtt

import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_ONLINE_LEADERBOARD_REQUEST
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_ONLINE_LEADERBOARD_RESP
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_ONLINE_QUERY_NUM_REQUEST
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_ONLINE_QUERY_NUM_RESP
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import kotlinx.coroutines.DelicateCoroutinesApi
import sjj.alog.Log

@OptIn(DelicateCoroutinesApi::class)
object OnlineInfos {
    val onlineInfo = MutableLiveData<OnlineInfo>()

    suspend fun refresh() {
        val payload = mapOf(
            "clientId" to globalConfig.mqttClientId,
            "period" to 10 * 60 //请求30分钟内在线的客户端列表
        )
        val response = MqttUtil.request(TOPIC_ONLINE_QUERY_NUM_REQUEST, TOPIC_ONLINE_QUERY_NUM_RESP, gson.toJson(payload).toByteArray()) ?: return
        val online = gson.fromJson<OnlineInfo>(String(response)) ?: return
        onlineInfo.postValue(online)
    }

    /**
     *period 统计周期 day / week / month / year / total
     */
    suspend fun getOnlineLeaderboard(period: String): OnlineLeaderboard? {
        val payload = mapOf(
            "clientId" to globalConfig.mqttClientId,
            "period" to period, //统计周期 day / week / month / year / total
            "limit" to 20, //返回条数
            "offset" to 0 //分页偏移
        )
        val response = MqttUtil.request(TOPIC_ONLINE_LEADERBOARD_REQUEST, TOPIC_ONLINE_LEADERBOARD_RESP, gson.toJson(payload).toByteArray()) ?: return null
        return gson.fromJson<OnlineLeaderboard>(String(response))
    }
}

class OnlineLeaderboard {
    var period: String = ""
    var period_start: Long = 0
    var period_end: Long = 0
    var total: Int = 0
    var limit: Int = 0
    var offset: Int = 0
    var server_ts: Long = 0
    var items: List<RankingInfo>? = null
}

class RankingInfo {
    var rank: Int = 0
    var user_id: String = ""
    var online_seconds: Int = 0
    var total_online_seconds: Int = 0
    var level: LevelInfo? = null
}

class OnlineInfo {
    var client_id: String = ""
    var online_count: Int = 0
    var today_online_seconds: Int = 0
    var total_online_seconds: Int = 0
    var level: LevelInfo? = null

    var server_ts: Long = 0
}

fun Int.formatDuration(): String {
    val seconds: Int = this
    val safeSeconds = seconds.coerceAtLeast(0)
    val days = safeSeconds / 86400
    val hours = (safeSeconds % 86400) / 3600
    val minutes = (safeSeconds % 3600) / 60
    val remainSeconds = safeSeconds % 60
    return when {
        days > 0 -> "${days}天${hours}小时${minutes}分"
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分钟"
        else -> "${remainSeconds}秒"
    }
}

class LevelInfo {
    var major: String = ""
    var stage: Int = 0
    var stage_name: String = ""
    var level_index: Int = 0
    var progress: Double = 0.0
    var current_stage_required_seconds: Int = 0
    var next_level_need_seconds: Int = 0
    var is_max_level: Boolean = false


    val levelName: String
        get() = when {
            stage_name.isNotBlank() -> stage_name
            major.isNotBlank() && stage > 0 -> "${major}${stage}层"
            major.isNotBlank() -> major
            else -> "未入门"
        }
}