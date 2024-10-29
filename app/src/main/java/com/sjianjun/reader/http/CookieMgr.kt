package com.sjianjun.reader.http

import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.sjianjun.reader.App
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

object CookieMgr : CookieJar {
    private val actualCookieJar =
        PersistentCookieJar(SetCookieCache(), SharedPrefsCookiePersistor(App.app))

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        actualCookieJar.saveFromResponse(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {

        return actualCookieJar.loadForRequest(url).toMutableList()
    }

    @JvmStatic
    fun getCookie(url: String): MutableList<Cookie> {
        return loadForRequest(url.toHttpUrl())
    }

    @JvmStatic
    fun getCookie(url: String, name: String): String {
        return loadForRequest(url.toHttpUrl()).find { it.name == name }?.value ?: ""
    }
}