package com.sjianjun.reader.online

import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.nickname
import java.util.UUID

/** Debug 包统一使用的固定在线统计标识，便于把调试数据归到同一条并在排行榜里查看 */
private const val DEBUG_DEVICE_ID = "debug-device"

/**
 * 在线统计使用的设备标识。
 * 沿用全局的 deviceID（换机或清数据会变），不再依赖 MQTT 的 clientId。
 * Debug 包固定使用 [DEBUG_DEVICE_ID]，避免每次安装/清数据都产生新标识。
 */
val onlineDeviceId: String
    get() {
        if (BuildConfig.DEBUG) return DEBUG_DEVICE_ID
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
        if (onlineDeviceId == this) {
            return "我(${nickname})"
        }
        return "书友(${nickname})"
    }
