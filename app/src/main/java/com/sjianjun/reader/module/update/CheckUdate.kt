package com.sjianjun.reader.module.update

import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.sjianjun.coroutine.withIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.*
import sjj.alog.Log
import java.util.concurrent.TimeUnit
import kotlin.math.max

suspend fun loadUpdateInfo() {
    if (System.currentTimeMillis() - globalConfig.lastCheckUpdateTime > TimeUnit.HOURS.toMillis(1)) {
        val info = http.get(
            URL_RELEASE_INFO,
            header = mapOf("Content-Type" to "application/json;charset=UTF-8")
        )
        globalConfig.releasesInfo = info
        if (!BuildConfig.DEBUG) {
            globalConfig.lastCheckUpdateTime = System.currentTimeMillis()
        }
        Log.i(info)
    }
}

suspend fun checkUpdate(fromUser: Boolean = false) = withIo {
    try {
        loadUpdateInfo()
    } catch (e: Exception) {
        if (fromUser) {
            toast("Github:版本信息加载失败，访问不稳定开启代理再试", Toast.LENGTH_LONG)
        }
        return@withIo
    }

    val releasesInfo = gson.fromJson<ReleasesInfo>(globalConfig.releasesInfo) ?: return@withIo

    val download = releasesInfo.apkAssets
    if (download?.browser_download_url.isNullOrEmpty()) {
        return@withIo
    }
    if (releasesInfo.prerelease && !(BuildConfig.DEBUG || fromUser)) {
        return@withIo
    }
    val lastVersion = lastVersion(BuildConfig.VERSION_NAME, releasesInfo.tag_name)
    if (lastVersion != BuildConfig.VERSION_NAME) {
        val currentActivity = ActivityManger.currentActivity
        val dialog = AlertDialog.Builder(currentActivity)
            .setTitle(if (releasesInfo.name.isEmpty()) "版本更新" else releasesInfo.name)
            .setMessage("发现新版本是否现在升级？\n${releasesInfo.body}")
            .setPositiveButton("下载") { dialog, _ ->
                dialog.dismiss()
                val service = ContextCompat.getSystemService(currentActivity, DownloadManager::class.java);
                service?.enqueue(
                    DownloadManager.Request(Uri.parse(download?.browser_download_url))
                        .setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            "${currentActivity.packageName}/学习${releasesInfo.tag_name}.apk"
                        )
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                )
            }
        withMain {
            dialog.show()
        }
    } else {
        if (fromUser) {
            toast("Github:已经是最新版本", Toast.LENGTH_LONG)
        }
    }

}

private fun lastVersion(version1: String, version2: String): String {
    if (version1 == version2) {
        return version1
    }
    val split1 = version1.split(".")
    val split2 = version2.split(".")
    (0..max(split1.size, split2.size)).forEach {
        val n1 = split1.getOrNull(it)?.toIntOrNull() ?: 0
        val n2 = split2.getOrNull(it)?.toIntOrNull() ?: 0
        if (n1 > n2) {
            return version1
        }
        if (n1 < n2) {
            return version2
        }
    }
    return version1
}