package com.sjianjun.reader.http

import kotlinx.coroutines.Deferred
import retrofit2.http.*

interface HttpInterface {

    @GET
    fun get(@Url url: String, @QueryMap queryMap: Map<String, String> = emptyMap(), @HeaderMap header: Map<String, String> = emptyMap()): Deferred<String>


    @FormUrlEncoded
    @POST
    fun post(@Url url: String = "", @FieldMap fieldMap: Map<String, String> = emptyMap(), @HeaderMap header: Map<String, String> = emptyMap()): Deferred<String>

}