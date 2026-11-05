package com.sjianjun.reader.bean

import androidx.room.Entity

/**
 * 广告拦截主机。[kind] 区分黑名单/白名单（"black"/"white"）。
 * [typeJson] 为原 [com.sjianjun.reader.module.bookcity.HostStr.type]（List<String>）的 JSON 序列化。
 * [mode] 为拦截/放行范围：顶级域名/子域名/精确URL（见 BLOCK_MODE_*）。
 */
@Entity(tableName = "ad_block_host", primaryKeys = ["host", "kind"])
data class AdBlockHostEntity(
    val host: String,
    val kind: String,
    val typeJson: String,
    val isPage: Boolean,
    val time: String,
    val mode: String,
    /** 最近一次命中时间（毫秒，0 = 从未命中），用于列表按最近命中时间倒序并按月分组 */
    val lastHitTime: Long = 0
)
