package com.sjianjun.reader.http

import androidx.appcompat.app.AppCompatActivity
import io.legado.app.help.http.BackstageWebView
import sjj.alog.Log

//StringEscapeUtils.unescapeJson
object WebViewClient {
    suspend fun getResponse(act: AppCompatActivity, url: String, headerMap: Map<String, String> = mapOf(), html: String = ""): String {
        val text = BackstageWebView(
            url = url,
            headerMap = headerMap,
            html = html,
            encode = "UTF-8",
            sourceRegex = null,
            overrideUrlRegex = null,
            javaScript = null,
            delayTime = 0
        ).geResponse(act)
        return text
    }
}