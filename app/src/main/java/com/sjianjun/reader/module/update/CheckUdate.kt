package com.sjianjun.reader.module.update

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.URL_RELEASE_INFO
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.utils.withIo
import com.sjianjun.reader.utils.withMain
import sjj.novel.util.fromJson
import sjj.novel.util.gson
import kotlin.math.max

suspend fun checkUpdate(activity: BaseActivity, force: Boolean = false) = withIo {
    val releasesInfo =
        if (force || System.currentTimeMillis() - globalConfig.lastCheckUpdateTime > 1 * 60 * 60 * 1000) {
            if (force) {
                toast("正在加载版本信息……")
            }
            val info =
                http.get(
                    URL_RELEASE_INFO,
                    header = mapOf("Content-Type" to "application/json;charset=UTF-8")
                )
            if (info.isNotEmpty()) {
                globalConfig.releasesInfo = info
                globalConfig.lastCheckUpdateTime = System.currentTimeMillis()
                if (force) {
                    toast("版本信息加载成功")
                }
                gson.fromJson<ReleasesInfo>(info)
            } else {
                if (force) {
                    toast("版本信息加载失败，访问不稳定开启代理再试", Toast.LENGTH_LONG)
                }
                gson.fromJson<ReleasesInfo>(globalConfig.releasesInfo)
            }
        } else {
            gson.fromJson<ReleasesInfo>(globalConfig.releasesInfo)
        } ?: return@withIo null


    val download = releasesInfo.apkAssets
    if (download?.browser_download_url.isNullOrEmpty()) {
        return@withIo releasesInfo
    }
    if (releasesInfo.prerelease && !(BuildConfig.DEBUG || force)) {
        return@withIo releasesInfo
    }
    val lastVersion = lastVersion(BuildConfig.VERSION_NAME, releasesInfo.tag_name)
    if (lastVersion != BuildConfig.VERSION_NAME) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (releasesInfo.name.isEmpty()) "版本更新" else releasesInfo.name)
            .setMessage("发现新版本是否现在升级？\n${releasesInfo.body}")
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
    return@withIo releasesInfo
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