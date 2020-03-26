package com.sjianjun.reader.http

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking

class HttpClient {
    val httpInterface = createRetrofit().create(HttpInterface::class.java)


    @JvmOverloads
    fun get(
        url: String,
        queryMap: Map<String, String> = emptyMap(),
        header: Map<String, String> = emptyMap()
    ) = runBlocking {
        return@runBlocking httpInterface.get(url, queryMap, header).await()
    }

    @JvmOverloads
    fun post(
        url: String,
        fieldMap: Map<String, String> = emptyMap(),
        header: Map<String, String> = emptyMap()
    ) = runBlocking {
        return@runBlocking httpInterface.post(url, fieldMap, header).await()
    }


    @JvmOverloads
    inline fun <reified T> getSync(
        url: String,
        queryMap: Map<String, String> = emptyMap(),
        header: Map<String, String> = emptyMap()
    ) = runBlocking {
        val resp = httpInterface.get(url, queryMap, header).await()
        return@runBlocking Gson().fromJson(resp,T::class.java)
    }

}