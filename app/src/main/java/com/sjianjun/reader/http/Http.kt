package com.sjianjun.reader.http

import com.sjianjun.reader.BuildConfig
import okhttp3.*
import sjj.alog.Log
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private fun header() = mutableMapOf(
    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
    "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
    "Connection" to "close",
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
    .apply {
        val trustAllCerts: Array<TrustManager> = arrayOf(object : X509TrustManager {

            override fun checkClientTrusted(
                chain: Array<out java.security.cert.X509Certificate>?,
                authType: String?
            ) {
            }

            override fun checkServerTrusted(
                chain: Array<out java.security.cert.X509Certificate>?,
                authType: String?
            ) {
            }

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                return arrayOf()
            }
        })
        // 创建一个 SSLContext，并使用上面创建的 TrustManager
        val sslContext: SSLContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        // 创建一个 OkHttpClient，并设置 SSL SocketFactory
        sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        hostnameVerifier { _, _ -> true }
    }
    .connectTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
//    .retryOnConnectionFailure(false)
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
    }.addInterceptor(
        HttpLoggingInterceptor { Log.i(it) }.setLevel(
            HttpLoggingInterceptor.Level.BODY
        )
    ).build()

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
    fun body(
        url: String,
        body: String,
        contentType: String = "application/json",
        header: Map<String, String> = emptyMap()
    ): Resp {
        val builder = Request.Builder()
            .url(HttpUrl.get(url))
            .post(RequestBody.create(MediaType.parse(contentType), body))
            .cacheControl(CacheControl.Builder().maxAge(1, TimeUnit.HOURS).build())
        header.forEach {
            builder.header(it.key, it.value)
        }
        val response = okClient.newCall(builder.build()).execute()
        return Resp(
            response.request().url().toString(),
            stringConverter.stringConverter(response.body())
        )
    }
}