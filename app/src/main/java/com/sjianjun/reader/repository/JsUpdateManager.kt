package com.sjianjun.reader.repository

import android.content.res.AssetManager
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.App
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.bean.AdBlackVersion
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.JsVersionInfo
import com.sjianjun.reader.preferences.AdBlockConfig
import com.sjianjun.reader.preferences.JsConfig
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.view.CustomWebView
import kotlinx.coroutines.delay
import org.eclipse.egit.github.core.Issue
import org.eclipse.egit.github.core.client.GitHubClient
import org.eclipse.egit.github.core.service.IssueService
import org.eclipse.egit.github.core.service.MilestoneService
import sjj.alog.Log
import java.util.concurrent.TimeUnit

object JsUpdateManager {
    suspend fun checkUpdate() {
        //检查本地文件更新
        withIo {
            Log.i("=========================开始检查JS脚本更新=================================")
            checkLocalAdBlackUpdate()
            checkLocalJsUpdate()
//            if (System.currentTimeMillis() - JsConfig.remoteJsCheckTime > TimeUnit.HOURS.toMillis(1)) {
//                while (true) {
//                    try {
//                        checkRemoteJsUpdate()
//                        if (!BuildConfig.DEBUG) {
//                            JsConfig.remoteJsCheckTime = System.currentTimeMillis()
//                        }
//                        break
//                    } catch (e: Exception) {
//                        Log.e("网站脚本配置加载失败", e)
//                        delay(60000)
//                    }
//                }
//            }
            Log.i("===========================JS脚本更新结束==================================")
        }
    }

    private suspend fun checkLocalAdBlackUpdate() {
        try {
            if (JsConfig.localAdblockVersion >= BuildConfig.VERSION_CODE) {
                return
            }
            checkAdBlackUpdate({
                loadAssets("adBlock/version.json")
            }, {
                loadAssets("adBlock/filterUrl.json")
            }, {
                loadAssets("adBlock/${it}")
            })
            if (!BuildConfig.DEBUG) {
                JsConfig.localAdblockVersion = BuildConfig.VERSION_CODE
            }
        } catch (e: Exception) {
            Log.e("广告拦截配置加载失败", e)
        }
    }

    private suspend fun checkLocalJsUpdate() {
        try {
            if (JsConfig.localJsVersion >= BuildConfig.VERSION_CODE) {
                return
            }

            var jsVersionInfos: List<JsVersionInfo>? = null
            checkJsUpdate({
                val json = loadAssets("js/version.json")
                Log.i("local:${json}")
                jsVersionInfos = gson.fromJson<List<JsVersionInfo>>(json)
                jsVersionInfos ?: emptyList()
            }, {
                loadAssets("js/${it.source}")
            })
            if (!BuildConfig.DEBUG) {
                JsConfig.localJsVersion = BuildConfig.VERSION_CODE
            }
        } catch (e: Exception) {
            Log.e("网站脚本配置加载失败", e)
        }
    }

    suspend fun checkRemoteJsUpdate() = withIo {
        var jsVersionInfos: List<JsVersionInfo>? = null
        var issues: List<Issue>? = null
        val client = GitHubClient()
        checkJsUpdate({

            val json = MilestoneService(client).getMilestone("SJJ-dot", "Reader", 1).description
            Log.i("remote:${json}")
            jsVersionInfos = gson.fromJson<List<JsVersionInfo>>(json)
            jsVersionInfos ?: emptyList()
        }, { js ->
            if (issues == null) {
                issues = IssueService(client).getIssues(
                    "SJJ-dot",
                    "Reader",
                    mapOf("milestone" to "1")
                )
                Log.i("remote ${issues?.size}:${issues?.joinToString(",") { it.title }}")
            }
            issues!!.find { it.title == js.source }?.body
        })
        jsVersionInfos?.also { list ->
            val allLocalSource = JsConfig.allJsSource.toMutableSet()
            list.forEach {
                allLocalSource.remove(it.source)
            }
            if (BuildConfig.DEBUG) {
                Log.e("DEBUG 忽略服务端被删除的内容。在正式版本下列的脚本将会被删除：${allLocalSource}")
            } else {
                JsConfig.removeJs(*allLocalSource.toTypedArray())
            }
        }

    }

    private suspend fun checkAdBlackUpdate(
        loadAdBlackVersionInfo: suspend () -> String,
        loadFilterUrls: suspend () -> String,
        loadWebsite: suspend (source: String) -> String,
    ) {
        val json = loadAdBlackVersionInfo()
        val adBlackVersion = gson.fromJson<AdBlackVersion>(json) ?: return
        if (AdBlockConfig.adBlockFilterUrlVersion < adBlackVersion.filterUrlVersion) {
            val urlSet = gson.fromJson<List<String>>(loadFilterUrls())
            if (urlSet != null) {
                AdBlockConfig.adBlockList = urlSet.map { CustomWebView.AdBlock(it) }
            }
            if (!BuildConfig.DEBUG) {
                AdBlockConfig.adBlockFilterUrlVersion = adBlackVersion.filterUrlVersion
            }
        }
        adBlackVersion.sourceVersions?.forEach {
            if (it.version > AdBlockConfig.getAdBlockJsVersion(it.source)) {
                AdBlockConfig.saveAdBlockJs(it.source, loadWebsite(it.source))
                if (!BuildConfig.DEBUG) {
                    AdBlockConfig.saveAdBlockJsVersion(it.source, it.version)
                }
            }
        }
    }

    private suspend fun checkJsUpdate(
        loadVersionInfo: suspend () -> List<JsVersionInfo>,
        loadJs: suspend (JsVersionInfo) -> String?
    ) {
        val jsVersionInfos = loadVersionInfo()
        jsVersionInfos.forEach {
            val localVersion = JsConfig.getJs(it.source)?.version ?: 0
            if (localVersion < it.version || (BuildConfig.DEBUG && localVersion == it.version)) {
                loadJs(it)?.let { js ->
                    val b = JsConfig.getJs(it.source)?.enable ?: true
                    JsConfig.saveJs(JavaScript(it.source, js, it.version, it.starting, it.priority, b))
                }

            }
        }
    }

    private fun loadAssets(fileName: String): String {
        return App.app.assets.open(fileName, AssetManager.ACCESS_BUFFER).use { stream ->
            stream.bufferedReader().readText()
        }
    }
}