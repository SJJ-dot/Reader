package com.sjianjun.reader.utils

import android.app.Activity
import android.widget.Toast
import com.azhon.appupdate.listener.OnDownloadListener
import com.azhon.appupdate.manager.DownloadManager
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.http.http
import com.sjianjun.reader.preferences.appEssentials
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import sjj.alog.Log
import java.io.File
import kotlin.coroutines.resume


enum class Channels {
    PGY {
        override suspend fun getReleaseInfo(): ReleasesInfo {
            val _api_key = BuildConfig.PGY_API_KEY
            val appKey = BuildConfig.PGY_APP_KEY
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

suspend fun checkUpdate(fromUser: Boolean = true) = withIo {
    var releasesInfo = getReleaseInfo()
    if (releasesInfo == null) {
        releasesInfo = appEssentials.releasesInfo
    } else {
        appEssentials.releasesInfo = releasesInfo
    }

    Log.i("版本更新信息：${releasesInfo}")
    if (releasesInfo == null) {
        toast("版本信息加载失败", Toast.LENGTH_SHORT)
        return@withIo
    }
    if (releasesInfo.isUpgradeable()) {
        if (!fromUser && appEssentials.ignoredUpdateVersion == releasesInfo.lastVersion) {
            Log.i("用户已忽略版本：${releasesInfo.lastVersion}，不再自动提示更新")
            return@withIo
        }
        suspendCancellableCoroutine { coroutine ->
            showUpdateDialog(ActivityManger.getTopActivity() ?: return@suspendCancellableCoroutine, releasesInfo) {
                coroutine.resume(Unit)
            }
        }
    } else {
        if (fromUser) {
            toast("当前已经是最新版本", Toast.LENGTH_SHORT)
        }
    }
    Log.e("检查更新完成")
    appEssentials.consecutiveCrashes = 0
}

/**
 * 进程内桥接：把更新弹窗“关闭 / 忽略 / 下载完成 / 下载失败”等收尾事件转交给调用方注册的
 * [onSettled]。库的 UpdateDialogActivity 在用户关闭或忽略弹窗时**不会**回调 OnDownloadListener，
 * 因此由自定义内容视图 [com.sjianjun.reader.view.AppUpdateDialogUpdate] 在对应按钮里调用 [UpdateSettleBridge.fire]
 * 来触发收尾（如进入主页、退出更新进程）。[fire] 保证整个弹窗生命周期内最多触发一次。
 */
object UpdateSettleBridge {
    private var onSettled: (() -> Unit)? = null
    private var fired = false

    fun set(callback: () -> Unit) {
        onSettled = callback
        fired = false
    }

    fun fire() {
        if (fired) return
        fired = true
        onSettled?.invoke()
        onSettled = null
    }
}

/**
 * 弹出更新对话框（强制升级）。下载完成 / 取消 / 失败 / 关闭 / 忽略均通过 [UpdateSettleBridge] 回调 [onSettled]，
 * 便于调用方决定后续动作（如拉起主页、退出更新子进程）。供更新子进程（:update）与既有检查更新逻辑复用。
 */
fun showUpdateDialog(ativity: Activity, info: ReleasesInfo, onSettled: () -> Unit = {}) {
    UpdateSettleBridge.set(onSettled)
    val manager = DownloadManager.Builder(ativity).run {
        apkUrl(info.downloadApkUrl!!)
        apkName("appupdate.apk")
        smallIcon(R.mipmap.ic_xue_xi)
        //设置了此参数，那么内部会自动判断是否需要显示更新对话框，否则需要自己判断是否需要更新
        apkVersionCode((AppInfoUtil.versionCode() + 100).toInt())
        //同时下面三个参数也必须要设置
        apkVersionName(info.lastVersion!!)
        apkDescription(info.updateContent!!)
        forcedUpgrade(true)
        onDownloadListener(object : OnDownloadListener {
            override fun cancel() {
                Log.e("取消更新")
                UpdateSettleBridge.fire()
            }

            override fun done(apk: File) {
//                UpdateSettleBridge.fire()
            }

            override fun downloading(max: Int, progress: Int) {
            }

            override fun error(e: Throwable) {
                Log.e("APK下载失败：${e.message}")
                toast("APK下载失败：${e.message}")
                //下载失败更换下载渠道
                UpdateSettleBridge.fire()
            }

            override fun start() {
            }
        })
        //省略一些非必须参数...
        build()
    }
    manager.download()
}
