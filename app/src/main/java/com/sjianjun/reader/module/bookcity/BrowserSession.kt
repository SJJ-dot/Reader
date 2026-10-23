package com.sjianjun.reader.module.bookcity

import com.tencent.mmkv.MMKV

/**
 * 浏览器会话恢复：
 * - 退出时回退栈还有页面 → 下次从书城进入时回到该页面；
 * - 已经退到底再退出 → 下次进入新建窗口（首页）。
 */
object BrowserSession {

    private val mmkv = MMKV.mmkvWithID("BrowserSession")
    private const val KEY_RESUME = "resume_url"

    fun saveResume(url: String?) {
        if (url.isNullOrBlank()) {
            mmkv.removeValueForKey(KEY_RESUME)
        } else {
            mmkv.encode(KEY_RESUME, url)
        }
    }

    /** 取出并清空恢复地址：只恢复一次 */
    fun consumeResume(): String? {
        val url = mmkv.decodeString(KEY_RESUME)
        mmkv.removeValueForKey(KEY_RESUME)
        return url?.takeIf { it.isNotBlank() }
    }

    /** 标记：下次进入浏览器时新建一个主页标签页 */
    fun markNewHomeTab() {
        mmkv.encode("new_home_tab", true)
    }

    fun consumeNewHomeTab(): Boolean {
        val value = mmkv.decodeBool("new_home_tab", false)
        mmkv.removeValueForKey("new_home_tab")
        return value
    }

    fun clear() {
        mmkv.removeValueForKey(KEY_RESUME)
    }
}
