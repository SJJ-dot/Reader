package com.sjianjun.reader.http

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl

// 自定义 CookieJar 用于同步 OkHttp 和 WebView 的 Cookie
class WebViewCookieJar : CookieJar {
    private val cookieManager: CookieManager = CookieManager.getInstance()
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


}
