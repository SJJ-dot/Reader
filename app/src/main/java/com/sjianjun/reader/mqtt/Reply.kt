package com.sjianjun.reader.mqtt


data class Reply(
    var author: String? = null,
    var content: String? = null,
    var timestamp: Long = System.currentTimeMillis()
)
