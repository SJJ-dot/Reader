package com.sjianjun.reader.online

import androidx.lifecycle.MutableLiveData

/**
 * 在线统计（原 MQTT 实现已迁移到云服务 RPC，见 [OnlineApi]）。
 */
object OnlineInfos {
    val onlineInfo = MutableLiveData<OnlineInfo>()

    /**
     * 拉取个人在线统计与在线人数。
     * 顺带把本地累计的在线时长上传一次（见 [OnlineRecorder.flushNow]），所以打开页面就能看到最新数据。
     */
    suspend fun refresh() {
        OnlineRecorder.flushNow()
    }

    /**
     * 排行榜。period 取 day / week / month / year / total，**除 total 外都是滑动窗口**：
     *
     * | period | 含义 |
     * |---|---|
     * | day   | 最近 24 小时（不是「当天」） |
     * | week  | 最近 7 天（不是「本周一到今天」） |
     * | month | 最近 30 天 |
     * | year  | 最近 365 天 |
     * | total | 全部累计，取 ol_users.total_seconds |
     */
    suspend fun getOnlineLeaderboard(period: String): OnlineLeaderboard? {
        return OnlineApi.leaderboard(period)
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

    /** 该设备上报的 APP 版本；老版本客户端不上报，可能为空 */
    var app_version: String = ""
    val levelName: String
        get() = "总阅读时长：" + total_online_seconds.formatDuration()
}

class OnlineInfo {
    var client_id: String = ""
    var online_count: Int = 0
    var today_online_seconds: Int = 0
    var total_online_seconds: Int = 0
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
