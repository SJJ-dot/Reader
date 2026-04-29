package com.sjianjun.reader.mqtt

import com.sjianjun.reader.preferences.globalConfig
import java.util.UUID


data class Feedback(
    var id: String = UUID.randomUUID().toString().replace("-", ""),
    var client_id: String? = null,
    var parent_id: String? = null,
    var content: String? = null,
    var created_at: Long = 0,
    var updated_at: Long = 0,
    var replies: List<Feedback>? = null,
)


data class FeedbackListResponse(
    val status: String? = null,
    val items: List<Feedback>? = null,
    val count: Int = 0,
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
)

val String?.user: String
    get() {
        if (this == null) return "佚名"
        if (globalConfig.mqttClientId == this) {
            return "我"
        }
        val id = (if (length >= 4) substring(0, 4) else this).uppercase()
        return "书友(${id})"
    }