package com.sjianjun.reader.http

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl

class CookieMgr : CookieJar{
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            cookieManager.setCookie(url.toString(), cookie.toString())
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookiesString = cookieManager.getCookie(url.toString())
        return if (cookiesString != null) {
            Cookie.parseAll(url, headersOf("Set-Cookie", cookiesString))
        } else {
            emptyList()
        }
    }

    companion object {
        private val cookieManager: CookieManager = CookieManager.getInstance()

        @JvmStatic
        fun getCookie(url: String): String {
            return cookieManager.getCookie(url) ?: ""
        }

        @JvmStatic
        fun getCookie(url: String, name: String): String {
            return cookieManager.getCookie(url)?.split(";")?.find { it.contains(name) } ?: ""
        }
    }

}