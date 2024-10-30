package com.sjianjun.reader.http

import android.webkit.CookieManager
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.sjianjun.reader.App
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class CookieMgr {


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