package com.sjianjun.reader.http

import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.sjianjun.okhttp3.interceptor.HttpLoggingInterceptor
import com.sjianjun.reader.App
import com.sjianjun.retrofit.converter.GsonCharsetCompatibleConverter
import com.sjianjun.retrofit.simple.http.HttpClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


val client = HttpClient.Builder()
    .apply {
        clientBuilder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .cookieJar(PersistentCookieJar(SetCookieCache(), SharedPrefsCookiePersistor(App.app)))
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
    }
    .addConverterFactory(GsonCharsetCompatibleConverter.create())
    .addCallAdapterFactory(CoroutineCallAdapterFactory())
    .build()