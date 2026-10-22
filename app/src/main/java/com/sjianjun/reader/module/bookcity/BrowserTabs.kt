package com.sjianjun.reader.module.bookcity

import com.google.gson.Gson
import com.tencent.mmkv.MMKV

/** 浏览器标签页（进程内共享，供 CustomWebView 与标签页界面使用） */
object BrowserTabs {

    data class Tab(
        var title: String,
        var url: String,
        var webView: MyWebView? = null,
        val id: java.util.UUID = java.util.UUID.randomUUID(),
        var thumbPath: String? = null,
    )

    val tabs = mutableListOf<Tab>()
    var activeIndex: Int = 0

    var onSelect: ((Int) -> Unit)? = null
    var onClose: ((Int) -> Unit)? = null
    var onNew: (() -> Unit)? = null

    /** Activity 重建时旧 WebView 已失效，清掉引用但保留标签信息 */
    fun detachWebViews() {
        tabs.forEach { it.webView = null }
    }
}
