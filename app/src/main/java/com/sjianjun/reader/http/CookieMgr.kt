package com.sjianjun.reader.http

import android.webkit.CookieManager
import sjj.alog.Log

object CookieMgr {

    private val cookieManager: CookieManager by lazy { CookieManager.getInstance() }

    // 新增方法：删除指定 URL 的 Cookie
    @Synchronized
    fun clearCookiesForUrl(url: String) {
        try {
            cookieManager.setAcceptCookie(true)
            val cookies = cookieManager.getCookie(url)
            cookies?.split(";")?.forEach { cookie ->
                val cookieName = cookie.substringBefore("=")
                cookieManager.setCookie(url, "$cookieName=; Expires=Thu, 01 Jan 1970 00:00:00 GMT") // 设置过期时间为过去的时间
            }
            cookieManager.flush()
            Log.d("Removed cookies for URL: $url")
        } catch (e: Exception) {
            Log.e("Error clearing cookies for URL: $url", e)
        }
    }

    @JvmStatic
    fun getCookie(url: String): String {
        return try {
            cookieManager.getCookie(url) ?: ""
        } catch (e: Exception) {
            Log.e("Error getting cookies for URL: $url", e)
            ""
        }
    }

    @JvmStatic
    fun setCookie(url: String, cookies: String) {
        try {
            cookieManager.setAcceptCookie(true)
            cookies.split(";").forEach { cookie ->
                if (cookie.isNotBlank()) {
                    cookieManager.setCookie(url, cookie.trim()) // 设置每个键值对
                }
            }
            cookieManager.flush()
            Log.i("Cookies set for URL: $url")
        } catch (e: Exception) {
            Log.e("Error setting cookies for URL: $url", e)
        }
    }
}