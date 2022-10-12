package com.sjianjun.reader.module.update

import android.app.Activity
import android.widget.Toast
import com.azhon.appupdate.manager.DownloadManager
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.*
import sjj.alog.Log
import java.util.concurrent.TimeUnit

suspend fun checkUpdate(ativity: Activity, fromUser: Boolean = false) = withIo {
    try {
        if (fromUser || System.currentTimeMillis() - globalConfig.lastCheckUpdateTime >
            TimeUnit.HOURS.toMillis(1)
        ) {
            val info = http.get(
                URL_RELEASE_INFO,
                header = mapOf("Content-Type" to "application/json;charset=UTF-8")
            )
            globalConfig.releasesInfo = info
            if (!BuildConfig.DEBUG) {
                globalConfig.lastCheckUpdateTime = System.currentTimeMillis()
            }
            val releasesInfo = gson.fromJson<ReleasesInfo>(info)
            Log.i(releasesInfo)
        }
    } catch (e: Exception) {
        if (fromUser) {
            toast("版本信息加载失败：${e.message}", Toast.LENGTH_LONG)
        }
        return@withIo
    }

    val releasesInfo = gson.fromJson<ReleasesInfo>(globalConfig.releasesInfo) ?: return@withIo

    val download = releasesInfo.apkAssets
    val browserDownloadUrl = download?.browser_download_url
    if (browserDownloadUrl.isNullOrEmpty()) {
        return@withIo
    }
    if (releasesInfo.prerelease && !(BuildConfig.DEBUG || fromUser)) {
        return@withIo
    }
    if (releasesInfo.isNewVersion) {

        val manager = DownloadManager.Builder(ativity).run {
            apkUrl(browserDownloadUrl)
            apkName(download.name)
            smallIcon(R.mipmap.ic_xue_xi)
            //设置了此参数，那么内部会自动判断是否需要显示更新对话框，否则需要自己判断是否需要更新
            apkVersionCode(BuildConfig.VERSION_CODE+1)
            //同时下面三个参数也必须要设置
            apkVersionName(releasesInfo.tag_name)
            apkSize("${(releasesInfo.apkAssets?.size ?: 0) / 1024 / 1024}MB")
            apkDescription(releasesInfo.body)
            //省略一些非必须参数...
            build()
        }

        manager.download()
    } else {
        if (fromUser) {
            toast("当前已经是最新版本", Toast.LENGTH_LONG)
        }
    }

}