package com.sjianjun.reader.module.update

import android.app.Activity
import android.widget.Toast
import com.alibaba.sdk.android.oss.model.GetObjectRequest
import com.azhon.appupdate.listener.OnDownloadListener
import com.azhon.appupdate.manager.DownloadManager
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONObject
import sjj.alog.Log
import java.io.File


enum class Channels {
    PGY {
        override suspend fun getReleaseInfo(): ReleasesInfo {
            val _api_key = AppInfoUtil.metaData("PGYER_API_KEY")
            val token = AppInfoUtil.metaData("PGYER_FRONTJS_KEY")
            val url =
                "https://www.pgyer.com/apiv2/app/check?_api_key=${_api_key}&token=${token}&buildVersion=${AppInfoUtil.versionCode()}"
            val info = JSONObject(http.get(url).body).getJSONObject("data")

            val releasesInfo = ReleasesInfo()
            releasesInfo.channel = name
            releasesInfo.lastVersion = info.getString("buildVersion")
            releasesInfo.updateContent = info.getString("buildUpdateDescription")
            releasesInfo.downloadApkUrl = info.getString("downloadURL")
            return releasesInfo
        }
    },
    OSS {
        override suspend fun getReleaseInfo(): ReleasesInfo {
            val oss = OssUtil.getOSSClient()
            val request = GetObjectRequest("reader-repo", "releases/checkUpdate.json")
            val body = oss.getObject(request).objectContent.reader().readText()
            val releasesInfo = gson.fromJson<ReleasesInfo>(body)!!
            releasesInfo.channel = name
            val url =
                oss.presignConstrainedObjectURL(
                    "reader-repo",
                    "releases/${releasesInfo.lastVersion}/app.apk",
                    24 * 60 * 60
                )
            releasesInfo.downloadApkUrl = url
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

suspend fun getReleaseInfo(): ReleasesInfo? = withIo {
    val awaitAll = Channels.values().map {
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

suspend fun checkUpdate(ativity: Activity) = withIo {
    var releasesInfo = gson.fromJson<ReleasesInfo>(globalConfig.releasesInfo)
    if (releasesInfo?.isUpgradeable() != true) {
        val netInfo = getReleaseInfo()
        if (netInfo != null) {
            if (releasesInfo == null || netInfo > releasesInfo) {
                releasesInfo = netInfo
                globalConfig.releasesInfo = gson.toJson(netInfo)
            }
        }
    }
    Log.i("版本更新信息：${releasesInfo}")
    if (releasesInfo == null) {
        toast("版本信息加载失败", Toast.LENGTH_SHORT)
        return@withIo
    }
    if (releasesInfo.isUpgradeable()) {
        val manager = DownloadManager.Builder(ativity).run {
            apkUrl(releasesInfo.downloadApkUrl!!)
            apkName("reader-${releasesInfo.lastVersion}.apk")
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
        toast("当前已经是最新版本", Toast.LENGTH_SHORT)
    }
}