package com.sjianjun.reader.online

import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.nickname
import java.util.UUID

/**
 * 在线统计使用的设备标识。
 * 沿用全局的 deviceID（换机或清数据会变），不再依赖 MQTT 的 clientId。
 */
val onlineDeviceId: String
    get() {
        val existing = globalConfig.deviceID
        if (!existing.isNullOrEmpty()) return existing
        val created = UUID.randomUUID().toString().replace("-", "")
        globalConfig.deviceID = created
        return created
    }

/**
 * 排行榜里按设备标识区分「我 / 书友」，昵称由标识确定性派生（同一标识在任何设备上显示一致）。
 */
val String?.user: String
    get() {
        if (this == null) return "佚名"
        val nickname = nickname
        if (globalConfig.deviceID == this) {
            return "我(${nickname})"
        }
        return "书友(${nickname})"
    }
