package com.sjianjun.test.http

import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

object CookieMgr : CookieJar {
    private val actualCookieJar =
        PersistentCookieJar(SetCookieCache())
    override fun saveFromResponse(url: HttpUrl, cookies: MutableList<Cookie>) {

        actualCookieJar.saveFromResponse(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {

        return actualCookieJar.loadForRequest(url)
    }

    @JvmStatic
    fun getCookie(url: String): MutableList<Cookie> {
        return loadForRequest(HttpUrl.get(url))
    }

    @JvmStatic
    fun getCookie(url: String, name: String): String {
        return loadForRequest(HttpUrl.get(url)).find { it.name() == name }?.value() ?: ""
    }
}