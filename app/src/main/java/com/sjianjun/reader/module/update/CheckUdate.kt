package com.sjianjun.reader.module.update

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat.startActivity
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.toastSHORT
import com.sjianjun.reader.utils.withIo
import com.sjianjun.reader.utils.withMain
import sjj.novel.util.fromJson
import sjj.novel.util.gson
import kotlin.math.max

suspend fun checkUpdate(activity: BaseActivity, force: Boolean = false) = withIo {
    val githubApi =
        if (force || System.currentTimeMillis() - globalConfig.lastCheckUpdateTime > 1 * 60 * 60 * 1000) {
            if (force) {
                toastSHORT("正在加载版本信息……")
            }
            val info =
                http.get("https://api.github.com/repos/SJJ-dot/Reader/releases/latest")
            if (info.isNotEmpty()) {
                globalConfig.releasesInfo = info
                globalConfig.lastCheckUpdateTime = System.currentTimeMillis()
                gson.fromJson<ReleasesInfo>(info)
            } else {
                if (force) {
                    toastSHORT("版本信息加载失败")
                }
                gson.fromJson<ReleasesInfo>(globalConfig.releasesInfo)
            }
        } else {
            gson.fromJson<ReleasesInfo>(globalConfig.releasesInfo)
        } ?: return@withIo null


    val download = githubApi.apkAssets
    if (download?.browser_download_url.isNullOrEmpty()) {
        return@withIo githubApi
    }

    val lastVersion = lastVersion(BuildConfig.VERSION_NAME, githubApi.tag_name)
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
    } else {
        if (force) {
            toastSHORT("当前已是最新版本")
        }
    }
    return@withIo githubApi
}

private fun lastVersion(version1: String, version2: String): String {
    if (version1 == version2) {
        return version1
    }
    val split1 = version1.split(".")
    val split2 = version2.split(".")
    (0..max(split1.size, split2.size)).forEach {
        val n1 = split1.getOrNull(it)?.toIntOrNull() ?: return version2
        val n2 = split2.getOrNull(it)?.toIntOrNull() ?: return version1
        if (n1 > n2) {
            return version1
        }
        if (n1 < n2) {
            return version2
        }
    }
    return version1
}