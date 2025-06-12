package com.sjianjun.reader.utils

import android.app.Activity
import android.widget.Toast
import com.azhon.appupdate.listener.OnDownloadListener
import com.azhon.appupdate.manager.DownloadManager
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.globalConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONObject
import sjj.alog.Log
import java.io.File


enum class Channels {
    PGY {
        override suspend fun getReleaseInfo(): ReleasesInfo {
            val _api_key = "fbdcc7e3fea0c654fed879db614f9031"
            val appKey = "80fa89dd88b73aff5f8c1dfbacc0ae6a"
            val url =
                "https://www.pgyer.com/apiv2/app/check?_api_key=${_api_key}&appKey=${appKey}"
            val info = JSONObject(http.get(url).body).getJSONObject("data")

            val releasesInfo = ReleasesInfo()
            releasesInfo.channel = name
            releasesInfo.lastVersion = info.getString("buildVersion")
            releasesInfo.updateContent = info.getString("buildUpdateDescription")
            releasesInfo.downloadApkUrl = info.getString("downloadURL")
            return releasesInfo
        }
    },
    Github {
        override suspend fun getReleaseInfo(): ReleasesInfo {
            try {
                val url = "https://api.github.com/repos/SJJ-dot/Reader/releases/latest"
                val info = JSONObject(http.get(url).body)

                val releasesInfo = ReleasesInfo()
                releasesInfo.channel = name
                releasesInfo.lastVersion = info.getString("tag_name")
                releasesInfo.updateContent = info.getString("body")
                releasesInfo.downloadApkUrl =
                    info.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
                globalConfig.releasesInfoGithub = releasesInfo
                return releasesInfo
            } catch (e: Exception) {
                Log.e("GitHub 版本信息加载失败")
                return globalConfig.releasesInfoGithub!!
            }
        }
    };

    abstract suspend fun getReleaseInfo(): ReleasesInfo

}

suspend fun getReleaseInfo(): ReleasesInfo? = withIo {
    val awaitAll = Channels.entries.map {
        async {
            try {
                it to it.getReleaseInfo()
            } catch (e: Exception) {
                Log.i("版本信息加载失败：${it}", e)
                null
            }
        }
    }.awaitAll().filterNotNull()

    val list = awaitAll.sortedWith(Comparator { o1, o2 ->
        val compareTo = o1.second.compareTo(o2.second)
        if (compareTo == 0) {
            return@Comparator o1.first.compareTo(o2.first)
        }
        return@Comparator -compareTo
    })
    return@withIo list.firstOrNull()?.second
}

suspend fun checkUpdate(ativity: Activity, fromUser: Boolean = true) = withIo {
    var releasesInfo = getReleaseInfo()
    if (releasesInfo == null) {
        releasesInfo = globalConfig.releasesInfo
    } else {
        globalConfig.releasesInfo = releasesInfo
    }

    Log.i("版本更新信息：${releasesInfo}")
    if (releasesInfo == null) {
        toast("版本信息加载失败", Toast.LENGTH_SHORT)
        return@withIo
    }
    if (releasesInfo.isUpgradeable()) {
        val manager = DownloadManager.Builder(ativity).run {
            apkUrl(releasesInfo.downloadApkUrl!!)
            apkName("appupdate.apk")
            smallIcon(R.mipmap.ic_xue_xi)
            //设置了此参数，那么内部会自动判断是否需要显示更新对话框，否则需要自己判断是否需要更新
            apkVersionCode((AppInfoUtil.versionCode() + 100).toInt())
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
            toast("当前已经是最新版本", Toast.LENGTH_SHORT)
        }
    }
}