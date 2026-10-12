package com.sjianjun.reader.module.feedback

import com.sjianjun.reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sjj.alog.Log
import java.util.concurrent.TimeUnit

/**
 * 反馈数据面：由原生代发云服务 RPC。
 *
 * 为什么不让网页自己请求：页面一旦从安装包加载，它的 Origin 就变成
 * appassets.androidplatform.net，不在云数据面的白名单里，直连会被 403 拒绝。
 * 所以页面只负责界面，所有读写都经 [FeedbackBridge] 转到原生，由这里发出。
 *
 * 数据面与云服务同域名，网关按 `/.cloud/` 前缀分流；
 * `x-wb-webapp-access-key` 是可公开的应用标识（网页源码里本来就有），不是密钥。
 */
object FeedbackApi {

    private const val ACCESS_KEY = "wbpk_GurkJIyVfddn7d1Fy97KW3_1Hxu1cMwwQTVPAxifhbyjErjtTLqquO4"
    private const val TIMEOUT_SECONDS = 15L
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val rpcBase: String
        get() = BuildConfig.FEEDBACK_URL.trimEnd('/') + "/.cloud/database/rest/rpc"

    private val h5Base: String
        get() = BuildConfig.FEEDBACK_URL.trimEnd('/') + "/h5"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 调用云函数并返回原始 JSON 文本（页面自己解析）。
     * [argsJson] 是页面传来的参数 JSON；调用失败返回 null，由桥转成错误对象。
     */
    suspend fun call(function: String, argsJson: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$rpcBase/$function")
                .post(argsJson.toRequestBody(JSON))
                .header("x-wb-webapp-access-key", ACCESS_KEY)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string()
                if (!response.isSuccessful) {
                    Log.e("反馈接口 $function 返回 ${response.code}")
                    return@withContext null
                }
                text
            }
        } catch (e: Exception) {
            Log.e("反馈接口 $function 调用失败: ${e.message}")
            null
        }
    }

    /** 只取线上 H5 的版本号（几十字节），用来判断要不要下载整包。 */
    suspend fun remoteVersion(): String? = get("$h5Base/version")

    /** 取整个 H5 资源包：版本号 + 三个文件的内容。 */
    suspend fun remoteBundle(): String? = get("$h5Base/bundle")

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("反馈热更新 $url 返回 ${response.code}")
                    return@withContext null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.e("反馈热更新 $url 失败: ${e.message}")
            null
        }
    }
}
