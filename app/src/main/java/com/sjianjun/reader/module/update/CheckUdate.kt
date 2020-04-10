package com.sjianjun.reader.module.update

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat.startActivity
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.bean.GithubApi
import com.sjianjun.reader.http.client
import com.sjianjun.reader.utils.CONTENT_TYPE_ANDROID
import com.sjianjun.reader.utils.withIo
import com.sjianjun.reader.utils.withMain
import sjj.alog.Log
import java.lang.RuntimeException

suspend fun checkUpdate(activity: BaseActivity) = withIo {
    val githubApi =
        client.get<GithubApi>(
            "https://api.github.com/repos/SJJ-dot/Reader/releases/latest",
            header = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.87 Safari/537.36")
        )
    val download = githubApi.assets?.find { it.content_type == CONTENT_TYPE_ANDROID }
    if (download?.browser_download_url.isNullOrEmpty()) {
        return@withIo githubApi
    }
    val lastVersion = listOf(githubApi.tag_name, BuildConfig.VERSION_NAME).first()
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