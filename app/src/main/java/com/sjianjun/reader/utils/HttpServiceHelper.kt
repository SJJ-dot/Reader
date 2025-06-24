package com.sjianjun.reader.utils

import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.bean.RobotVerify
import com.sjianjun.reader.http.CookieMgr
import com.sjianjun.reader.module.verification.WebViewVerificationActivity
import fi.iki.elonen.NanoHTTPD
import sjj.alog.Log

object HttpServiceHelper {
    var port = if (BuildConfig.DEBUG) 58888 else 38888 // 可以根据需要修改端口号
        private set
    private var server: LocalHttpServer? = null

    val isRunning = MutableLiveData<Boolean>(false)

    fun startHttpServer(): Boolean {
        stopHttpServer()
        Log.i("Starting HTTP server on port $port")
        try {
            server = LocalHttpServer(port)
            server?.start()
            Log.i("HTTP 服务已启动，端口：$port")
        } catch (e: Exception) {
            Log.e("HTTP 服务启动失败", e)
            port++
            server = null
            isRunning.value = false
            return false
        }
        try {
            // 注册mDNS服务
            MdnsHelper.registerService(port)
            isRunning.value = true
        } catch (e: Exception) {
            Log.e("Error starting HTTP server: ${e.message}")
            stopHttpServer()
            isRunning.value = false
        }
        return server != null
    }

    fun stopHttpServer() {
        try {
            Log.i("Stopping HTTP server on port $port")
            server?.stop()
            server = null
            isRunning.value = false
        } catch (e: Exception) {
            Log.e("Error stopping HTTP server: ${e.message}")
        }
    }
}

class LocalHttpServer(port: Int) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        Log.i("收到请求：$method $uri")
        if (method == Method.POST) {
            try {
                val files = mutableMapOf<String, String>()
                session.parseBody(files)
                val requestBody = files["postData"] // 获取 body 内容
                handleRequest(uri, requestBody ?: "")?.let { response ->
                    return response
                }
            } catch (e: Exception) {
                Log.e("解析请求体失败", e)
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "解析请求体失败")
            }
        }
        return newFixedLengthResponse("Hello, World!")
    }

    private fun handleRequest(uri: String, requestBody: String,): Response? {
        return try {
            when (uri) {
                "/start_verification_activity" -> {
                    val data = gson.fromJson<RobotVerify>(requestBody)!!
                    WebViewVerificationActivity.startAndWaitResult(data.url!!, data.headers ?: emptyMap(), data.html ?: "")
                    newFixedLengthResponse(CookieMgr.getCookie(data.url ?: ""))
                }
                "/getCookie" ->{
                    val data = gson.fromJson<Map<String, String>>(requestBody)!!
                    newFixedLengthResponse(CookieMgr.getCookie(data["url"] ?: ""))
                }
                "/setCookie" -> {
                    val data = gson.fromJson<Map<String, String>>(requestBody)!!
                    CookieMgr.setCookie(data["url"] ?: "", data["cookies"] ?: "")
                    newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Cookie 设置成功")
                }

                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "未找到资源")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "请求数据格式错误: ${e.message}")
        }
    }

}