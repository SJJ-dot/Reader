package com.sjianjun.reader.mqtt

import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.nickname

/**
 * 书友昵称显示：在线排行等 MQTT 场景按 clientId 区分「我 / 书友」。
 */
val String?.user: String
    get() {
        if (this == null) return "佚名"
        val nickname = nickname
        if (globalConfig.mqttClientId == this) {
            return "我(${nickname})"
        }
        return "书友(${nickname})"
    }
