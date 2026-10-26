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
    val mode: String
)
