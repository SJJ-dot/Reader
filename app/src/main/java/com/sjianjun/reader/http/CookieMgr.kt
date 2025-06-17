package com.sjianjun.reader.http

import android.webkit.CookieManager
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.sjianjun.reader.App
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import sjj.alog.Log

class CookieMgr {

    companion object {
        private val cache = SetCookieCache()
        private val persistor = SharedPrefsCookiePersistor(App.app)
        val cookieJar: PersistentCookieJar by lazy { PersistentCookieJar(cache, persistor) }

        // 新增方法：删除指定 URL 的 Cookie
        @Synchronized
        fun clearCookiesForUrl(url: String) {
            try {
                val httpUrl = url.toHttpUrl()
                val cookiesToRemove = cookieJar.loadForRequest(httpUrl)
                if (cookiesToRemove.isNotEmpty()) {
                    persistor.removeAll(cookiesToRemove) // 从持久化存储中移除
                    cookieJar.clearSession() // 清除会话中的 Cookie
                    Log.d("Removed cookies for URL: $url")
                } else {
                    Log.d("No cookies found for URL: $url")
                }
            } catch (e: Exception) {
                Log.e("Error clearing cookies for URL: $url", e)
            }
        }

        @JvmStatic
        fun getCookie(url: String): String {
            val httpUrl = url.toHttpUrl()
            val cookies = cookieJar.loadForRequest(httpUrl)
            return cookies.joinToString("; ") { "${it.name}=${it.value}" }
        }

        @JvmStatic
        fun getCookie(url: String, name: String): String {
            val httpUrl = url.toHttpUrl()
            val cookies = cookieJar.loadForRequest(httpUrl)
            return cookies.find { it.name == name }?.value ?: ""
        }

        @JvmStatic
        fun setCookie(url: String, cookies: String) {
            val httpUrl = url.toHttpUrl()
            val cookies1 = cookies.split(";").mapNotNull {
                val cookieParts = it.split("=")
                if (cookieParts.size == 2) {
                    Cookie.Builder()
                        .name(cookieParts[0].trim())
                        .value(cookieParts[1].trim())
                        .domain(httpUrl.host)
                        .path(httpUrl.encodedPath)
                        .build()
                } else {
                    null
                }
            }
            cookieJar.saveFromResponse(httpUrl, cookies1)
        }

        fun applyToWebView(url: String) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true) // 启用 Cookie 支持
            cookieManager.removeSessionCookies(null)
            cookieManager.removeAllCookies(null) // 清除所有 Cookie
            val cookies = getCookie(url).split(";")
            cookies.forEach {
                if (it.isNotBlank()) {
                    cookieManager.setCookie(url, it.trim())
                }
            }
            cookieManager.flush()
            Log.i("Cookies applied to WebView for URL: $url")
        }
    }

}