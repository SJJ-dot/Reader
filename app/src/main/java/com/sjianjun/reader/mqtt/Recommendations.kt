package com.sjianjun.reader.mqtt

import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_RECOMMENDATION_LIST
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_RECOMMENDATION_LIST_RESP
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_RECOMMENDATION_SET
import com.sjianjun.reader.mqtt.MqttUtil.TOPIC_RECOMMENDATION_SET_RESP
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson

object Recommendations {

    /**
     *   请求 payload 字段：
     *         - id (可选): 推荐记录ID。如果只传 id（其他字段为空），则删除该推荐
     *         - client_id: 请求方客户端ID
     *         - book_title: 书名（如果不传 id，此字段必填）
     *         - author: 作者（如果不传 id，此字段必填）
     *         - book_url: 书籍地址/购买链接（可选）
     */
    suspend fun setRecommendation(book_title: String, author: String, book_url: String?, recommend: Boolean): Boolean {
        val payload = mapOf(
            "client_id" to globalConfig.mqttClientId,
            "book_title" to book_title,
            "author" to author,
            "book_url" to book_url?.ifBlank { null },
            "recommend" to if (recommend) 1 else -1
        )
        val response = MqttUtil.request(TOPIC_RECOMMENDATION_SET, TOPIC_RECOMMENDATION_SET_RESP, gson.toJson(payload).toByteArray()) ?: return false
        val resp = gson.fromJson<RecommendationResponse>(String(response))
        return resp?.status == "ok"
    }

    suspend fun deleteRecommendation(id: String): Boolean {
        val payload = mapOf(
            "id" to id,
            "client_id" to globalConfig.mqttClientId
        )
        val response = MqttUtil.request(TOPIC_RECOMMENDATION_SET, TOPIC_RECOMMENDATION_SET_RESP, gson.toJson(payload).toByteArray()) ?: return false
        val resp = gson.fromJson<RecommendationResponse>(String(response))
        return resp?.status == "ok"
    }

    /**
     *        请求 payload 字段：
     *         - client_id: 请求方客户端ID
     *         - period: 更新时间周期，支持 day/week/month/total
     *           - day: 今日更新
     *           - week: 本周更新（从周一 00:00:00 起）
     *           - month: 本月更新（从每月 1 日 00:00:00 起）
     *           - total: 全量推荐（默认）
     *         - limit: 返回条数，默认 50，最大 100
     *         - offset: 分页偏移，默认 0
     */
    suspend fun getRecommendations(period: String = "total", limit: Int = 50, offset: Int = 0): List<Recommendation>? {
        val payload = mapOf(
            "client_id" to globalConfig.mqttClientId,
            "period" to period,
            "limit" to limit,
            "offset" to offset
        )
        val response = MqttUtil.request(TOPIC_RECOMMENDATION_LIST, TOPIC_RECOMMENDATION_LIST_RESP, gson.toJson(payload).toByteArray()) ?: return null
        val resp = gson.fromJson<RecommendationResponse>(String(response))
        return if (resp?.status == "ok") resp.items else null
    }
}


class RecommendationResponse {
    var status: String = ""
    var message: String? = null
    var items: List<Recommendation>? = null
    var count: Int = 0
    var total: Int = 0
    var limit: Int = 0
    var offset: Int = 0
}


class Recommendation {
    var id: String = ""
    var book_title: String = ""
    var author: String = ""
    var book_url: String = ""
    var recommendation_count: Int = 0
    var created_at: Long = 0
    var updated_at: Long = 0
}

