package com.sjianjun.reader.mqtt

import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
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