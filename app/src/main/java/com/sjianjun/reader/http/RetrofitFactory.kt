package com.sjianjun.reader.http

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.sjianjun.okhttp3.interceptor.HttpLoggingInterceptor
import com.sjianjun.retrofit.converter.GsonCharsetCompatibleConverter
import okhttp3.OkHttpClient
import retrofit2.Retrofit


fun createRetrofit(baseUrl: String = "https://github.com/SJJ-dot/Reader/"): Retrofit {
    val client = OkHttpClient
        .Builder()
        .addInterceptor(HttpLoggingInterceptor())
        .build()

    val retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl(baseUrl)
        .addConverterFactory(GsonCharsetCompatibleConverter.create())
        .addCallAdapterFactory(CoroutineCallAdapterFactory())
        .build()

    return retrofit
}