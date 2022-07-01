package com.sjianjun.reader.http

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.sjianjun.okhttp3.interceptor.HttpLoggingInterceptor
import com.sjianjun.reader.BuildConfig
import com.sjianjun.retrofit.converter.GsonCharsetCompatibleConverter
import com.sjianjun.retrofit.simple.http.HttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import sjj.alog.Log
import java.util.concurrent.TimeUnit

private fun header() = mutableMapOf(
    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
    "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
    "Connection" to "keep-alive",
    "sec-ch-ua" to """" Not;A Brand";v="99", "Microsoft Edge";v="103", "Chromium";v="103"""",
    "sec-ch-ua-mobile" to "?0",
    "sec-ch-ua-platform" to "\"Windows\"",
    "Sec-Fetch-Dest" to "document",
    "Sec-Fetch-Mode" to "navigate",
    "Sec-Fetch-Site" to "same-origin",
    "Upgrade-Insecure-Requests" to "1",
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.5060.53 Safari/537.36 Edg/103.0.1264.37"
)

val client = HttpClient.Builder()
    .apply {
        val spec = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
            .supportsTlsExtensions(true)
            .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
            .cipherSuites(
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_RC4_128_SHA,
                CipherSuite.TLS_ECDHE_RSA_WITH_RC4_128_SHA,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
                CipherSuite.TLS_DHE_DSS_WITH_AES_128_CBC_SHA,
                CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA
            )
            .build()
        clientBuilder = OkHttpClient.Builder()
            .connectionSpecs(listOf(spec, ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cookieJar(CookieMgr)
            .addInterceptor {

                val header = header()
                it.request().headers().names().forEach { name ->
                    header.remove(name)
                }
                val host = it.request().url().host()
                val newBuilder = it.request().newBuilder()
                newBuilder.addHeader("Host", host)
                newBuilder.addHeader("Referer", it.request().url().toString())
                header.forEach { (t, u) ->
                    newBuilder.addHeader(t, u)
                }
                it.proceed(newBuilder.build())
            }
        clientBuilder?.addInterceptor(
            HttpLoggingInterceptor { Log.i(it) }.setLevel(
                if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.HEADERS
                }

            )
        )

    }
    .addConverterFactory(GsonCharsetCompatibleConverter.create())
    .addCallAdapterFactory(CoroutineCallAdapterFactory())
    .build()

val http = Http()


class Http {

    @JvmOverloads
    fun get(
        url: String,
        queryMap: Map<String, String> = emptyMap(),
        header: Map<String, String> = emptyMap()
    ): String = runBlocking {
        client.get<String>(url, queryMap, header)
    }

    @JvmOverloads
    fun post(
        url: String,
        fieldMap: Map<String, String> = emptyMap(),
        header: Map<String, String> = emptyMap()
    ): String = runBlocking {
        try {
            client.post<String>(url, fieldMap, header)
        } catch (e: Exception) {
            Log.e("网络请求失败:$url", e)
            ""
        }
    }
}