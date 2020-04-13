package com.sjianjun.reader.module.update

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat.startActivity
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.bean.GithubApi
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.CONTENT_TYPE_ANDROID
import com.sjianjun.reader.utils.withIo
import com.sjianjun.reader.utils.withMain
import sjj.novel.util.fromJson
import sjj.novel.util.gson

suspend fun checkUpdate(activity: BaseActivity) = withIo {
    val header = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
        "Accept-Encoding" to "identity",
        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
        "Cache-Control" to "no-cache",
        "Connection" to "keep-alive",
        "Host" to "api.github.com",
        "Pragma" to "no-cache",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1"
    )
    val info =
        http.get("https://api.github.com/repos/SJJ-dot/Reader/releases/latest", header = header)
    if (info.isNotEmpty()) {
        globalConfig.releasesInfo = info
    }
    val githubApi = gson.fromJson<GithubApi>(globalConfig.releasesInfo) ?: return@withIo null

    val download = githubApi.assets?.find { it.content_type == CONTENT_TYPE_ANDROID }
    if (download?.browser_download_url.isNullOrEmpty()) {
        return@withIo githubApi
    }
    val lastVersion = listOf(githubApi.tag_name, BuildConfig.VERSION_NAME).max()!!
    if (lastVersion != BuildConfig.VERSION_NAME) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (githubApi.name.isEmpty()) "版本更新" else githubApi.name)
            .setMessage("发现新版本是否现在升级？\n${githubApi.body}")
            .setPositiveButton("下载") { dialog, _ ->
                dialog.dismiss()
                //浏览器
                val intent = Intent()
                intent.action = "android.intent.action.VIEW"
                intent.data = Uri.parse(download?.browser_download_url)
                startActivity(activity, intent, null)
            }
        withMain {
            dialog.show()
        }
    }
    return@withIo githubApi
}