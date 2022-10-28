package com.sjianjun.reader.module.update

import android.app.Activity
import android.widget.Toast
import com.azhon.appupdate.listener.OnDownloadListener
import com.azhon.appupdate.manager.DownloadManager
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.*
import org.json.JSONObject
import sjj.alog.Log
import java.io.File
import java.util.concurrent.TimeUnit

enum class Channels {
//    FastGit {
//        override suspend fun getReleaseInfo(): ReleasesInfo {
//            val url = "https://raw.fastgit.org/SJJ-dot/reader-repo/main/releases/checkUpdate.json"
//            val releasesInfo = gson.fromJson<ReleasesInfo>(http.get(url).body)!!
//            releasesInfo.channel = name
//            releasesInfo.downloadApkUrl =
//                "https://raw.fastgit.org/SJJ-dot/reader-repo/main/releases/${releasesInfo.lastVersion}/app.apk"
//            return releasesInfo
//        }
//    },
    IqiqIo {
        override suspend fun getReleaseInfo(): ReleasesInfo {
            val url = "https://raw.iqiq.io/SJJ-dot/reader-repo/main/releases/checkUpdate.json"
            val releasesInfo = gson.fromJson<ReleasesInfo>(http.get(url).body)!!
            releasesInfo.channel = name
            releasesInfo.downloadApkUrl =
                "https://raw.iqiq.io/SJJ-dot/reader-repo/main/releases/${releasesInfo.lastVersion}/app.apk"
            return releasesInfo
        }
    },
    Github {
        override suspend fun getReleaseInfo(): ReleasesInfo {
            val url = "https://api.github.com/repos/SJJ-dot/Reader/releases/latest"
            val info = JSONObject(http.get(url).body)

            val releasesInfo = ReleasesInfo()
            releasesInfo.channel = name
            releasesInfo.lastVersion = info.getString("tag_name")
            releasesInfo.updateContent = info.getString("body")
            releasesInfo.downloadApkUrl =
                info.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
            return releasesInfo
        }
    };

    abstract suspend fun getReleaseInfo(): ReleasesInfo

}

suspend fun checkUpdate(ativity: Activity, fromUser: Boolean = false) = withIo {
    if (!fromUser && System.currentTimeMillis() - globalConfig.lastCheckUpdateTime <
        TimeUnit.MINUTES.toMillis(30)
    ) {
        return@withIo
    }
    var releasesInfo: ReleasesInfo? = null
    for (ch in Channels.values()) {
        try {
            releasesInfo = Channels.values()[globalConfig.downloadChannel].getReleaseInfo()
            globalConfig.releasesInfo = gson.toJson(releasesInfo)
            break
        } catch (e: Exception) {
            Log.e("版本信息加载失败：${e.message}")
            globalConfig.downloadChannel =
                (globalConfig.downloadChannel + 1) % Channels.values().size
        }
    }
    if (releasesInfo == null && !fromUser) {
        val info = globalConfig.releasesInfo
        if (info != null) {
            releasesInfo = gson.fromJson<ReleasesInfo>(info)!!
        }
    }
    if (releasesInfo == null) {
        if (fromUser) {
            toast("版本信息加载失败", Toast.LENGTH_LONG)
        }
        return@withIo
    }
    globalConfig.lastCheckUpdateTime = System.currentTimeMillis()

    if (releasesInfo.isNewVersion) {
        val manager = DownloadManager.Builder(ativity).run {
            apkUrl(releasesInfo.downloadApkUrl!!)
            apkName("reader-${releasesInfo.lastVersion}.apk")
            smallIcon(R.mipmap.ic_xue_xi)
            //设置了此参数，那么内部会自动判断是否需要显示更新对话框，否则需要自己判断是否需要更新
            apkVersionCode(BuildConfig.VERSION_CODE + 1)
            //同时下面三个参数也必须要设置
            apkVersionName(releasesInfo.lastVersion!!)
            apkDescription(releasesInfo.updateContent!!)
            onDownloadListener(object : OnDownloadListener {
                override fun cancel() {
                }

                override fun done(apk: File) {
                }

                override fun downloading(max: Int, progress: Int) {
                }

                override fun error(e: Throwable) {
                    toast("APK下载失败：${e.message}")
                    //下载失败更换下载渠道
                    globalConfig.downloadChannel =
                        (globalConfig.downloadChannel + 1) % Channels.values().size
                }

                override fun start() {
                }
            })
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