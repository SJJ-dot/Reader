package com.sjianjun.test.http

import com.google.gson.JsonObject
import com.sjianjun.test.utils.Log
import com.sjianjun.test.utils.fromJson
import com.sjianjun.test.utils.gson
import okhttp3.*
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
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

val okClient = OkHttpClient.Builder()
//    .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890)))
    .connectionSpecs(
        listOf(
            ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
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
                ).build(),
            ConnectionSpec.MODERN_TLS,
            ConnectionSpec.CLEARTEXT
        )
    )
    .connectTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .cookieJar(CookieMgr)
    .addInterceptor(
        HttpLoggingInterceptor { Log.i(it) }.setLevel(
            HttpLoggingInterceptor.Level.BODY
        )
    )
    .cache(Cache(File("./cache/http/"), 100 * 1024 * 1024))
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
    }.build()

val stringConverter = StringConverter()

val http = Http()

class Resp(val url: String, val body: String)

class Http {

    @JvmOverloads
    fun get(
        url: String,
        queryMap: Map<String, String> = emptyMap(),
        header: Map<String, String> = emptyMap(),
        encoded: Boolean = true
    ): Resp {
        val urlBuilder = HttpUrl.get(url).newBuilder()
        queryMap.forEach {
            if (encoded) {
                urlBuilder.addEncodedQueryParameter(it.key, it.value)
            } else {
                urlBuilder.addQueryParameter(it.key, it.value)
            }
        }
        val builder = Request.Builder().url(urlBuilder.build())
            .cacheControl(
                CacheControl.Builder()
                    .maxAge(1, TimeUnit.HOURS)
                    .build()
            )
        header.forEach {
            builder.header(it.key, it.value)
        }
        val response = okClient.newCall(builder.build()).execute()
        return Resp(
            response.request().url().toString(),
            stringConverter.stringConverter(response.body())
        )
    }

    @JvmOverloads
    fun post(
        url: String,
        fieldMap: Map<String, String> = emptyMap(),
        header: Map<String, String> = emptyMap(),
        encoded: Boolean = true
    ): Resp {
        val formBody = FormBody.Builder()
        fieldMap.forEach {
            if (encoded) {
                formBody.addEncoded(it.key, it.value)
            } else {
                formBody.add(it.key, it.value)
            }

        }
        val builder = Request.Builder().url(HttpUrl.get(url)).post(formBody.build())
            .cacheControl(
                CacheControl.Builder()
                    .maxAge(1, TimeUnit.HOURS)
                    .build()
            )
        header.forEach {
            builder.header(it.key, it.value)
        }
        val response = okClient.newCall(builder.build()).execute()
        return Resp(
            response.request().url().toString(),
            stringConverter.stringConverter(response.body())
        )
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val resp = http.get(
                "https://sp0.baidu.com/5a1Fazu8AA54nxGko9WTAnF6hhy/su", mapOf(
                    "wd" to "我在精神病院",
                    "cb" to "f1",
                ), encoded = false
            )
            val respJson  = Regex("f1\\((.*)\\);").find(resp.body)?.groupValues?.getOrNull(1)
            val jarr = gson.fromJson(respJson,JsonObject::class.java).getAsJsonArray("s")
            println(gson.fromJson<List<String>>(jarr.toString()))
        }
    }

}