package com.sjianjun.reader.module.browser

import android.os.Bundle
import java.util.UUID

/**
 * 单个浏览器标签页的运行时数据。
 * - 入库字段：id / title / url / thumbPath / transient / lastUsedAt / orderIndex / isActive / sourceTabId
 * - 运行时字段（不入库）：state / stateUrl —— WebView 原生状态快照，用于 Fragment 重建 / 切换时恢复前进后退历史
 *
 * 不再像旧版那样把 WebView 实例挂在全局单例上；WebView 只存在于其所属的 TabFragment 内。
 *
 * [sourceTabId]：来源页标签 id（长按“新标签页打开”时记录）。子标签返回退出时自动切回来源页；
 * 一旦用户主动退出（菜单退出 / 切书架），[TabRepository.unlinkAll] 会清空它，使两标签相互独立。
 */
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var url: String = "",
    var thumbPath: String? = null,
    var transient: Boolean = false,
    var lastUsedAt: Long = System.currentTimeMillis(),
    /** 来源页标签 id（长按“新标签页打开”时记录）；空字符串表示无来源（普通标签） */
    var sourceTabId: String = "",
    /** 运行时（不入库）：WebView 原生状态快照（含前进/后退历史与当前页 index） */
    var state: Bundle? = null,
    /** 快照保存时所在页面 URL（= 当时 wv.url），用于恢复后对齐 tab.url */
    var stateUrl: String? = null,
)
