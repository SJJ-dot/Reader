package com.sjianjun.reader.mqtt

import java.util.UUID


data class Feedback(
    var id: String = UUID.randomUUID().toString().replace("-", ""),
    var clientId: String? = null,
    var content: String? = null,
    var timestamp: Long = System.currentTimeMillis(),
    // legacy compatibility fields
    var reply: String? = null,
    var repliedAt: Long? = null,
    var replies: MutableList<Reply> = mutableListOf()
)

// helper copy extension to produce a shallow copy with a new mutable replies list
fun Feedback.copy(): Feedback {
    val f = Feedback(id = this.id, clientId = this.clientId, content = this.content, timestamp = this.timestamp)
    f.reply = this.reply
    f.repliedAt = this.repliedAt
    f.replies = ArrayList(this.replies)
    return f
}