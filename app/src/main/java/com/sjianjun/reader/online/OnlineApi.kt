package com.sjianjun.reader.online

import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sjj.alog.Log
import java.util.concurrent.TimeUnit

/**
 * 在线统计接口：直连云服务 RPC，替代原来的 MQTT（EMQX）链路。
 *
 * 数据面与云服务同域名，网关按 `/.cloud/` 前缀分流到数据面，
 * 靠 `x-wb-webapp-access-key` 请求头识别是哪个应用（不需要 Origin）。
 * 该 key 是可公开的应用标识（网页源码里本来就有），不是密钥。
 */
object OnlineApi {

    private const val ACCESS_KEY = "wbpk_GurkJIyVfddn7d1Fy97KW3_1Hxu1cMwwQTVPAxifhbyjErjtTLqquO4"
    private const val TIMEOUT_SECONDS = 10L
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val rpcBase: String
        get() = BuildConfig.FEEDBACK_URL.trimEnd('/') + "/.cloud/database/rest/rpc"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 批量上报本地累计的在线时长，并返回最新统计。
     *
     * 服务端把 [items] 里各天的秒数累加进排行榜（累加前会校验日期与数值范围），
     * 顺带上报 APP 版本（[BuildConfig.VERSION_NAME]），排行榜里会显示，便于看出书友用的版本。
     * [items] 为空表示只报活一次（用于刷新在线人数），不计时长。
     */
    suspend fun report(deviceId: String, items: List<DailySeconds>): OnlineInfo? {
        val body = gson.toJson(
            mapOf(
                "p_device" to deviceId,
                "p_version" to BuildConfig.VERSION_NAME,
                "p_items" to items
            )
        )
        val text = post("ol_report", body) ?: return null
        return runCatching { gson.fromJson<OnlineInfo>(text) }.getOrNull()
    }

    /** 在线排行榜：period 取 day / week / month / year / total。 */
    suspend fun leaderboard(period: String): OnlineLeaderboard? {
        val body = gson.toJson(
            mapOf(
                "p_period" to period,
                "p_limit" to 250,
                "p_offset" to 0
            )
        )
        val text = post("ol_rank", body) ?: return null
        return runCatching { gson.fromJson<OnlineLeaderboard>(text) }.getOrNull()
    }

    private suspend fun post(function: String, body: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$rpcBase/$function")
                .post(body.toRequestBody(JSON))
                .header("x-wb-webapp-access-key", ACCESS_KEY)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("在线统计接口 $function 返回 ${response.code}")
                    return@withContext null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.e("在线统计接口 $function 调用失败: ${e.message}")
            null
        }
    }
}
