package com.sjianjun.reader.repository

import android.content.res.AssetManager
import com.sjianjun.coroutine.withIo
import com.sjianjun.reader.App
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.bean.AdBlackVersion
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.JsVersionInfo
import com.sjianjun.reader.preferences.AdBlockConfig
import com.sjianjun.reader.preferences.JsConfig
import com.sjianjun.reader.utils.fromJson
import com.sjianjun.reader.utils.gson
import com.sjianjun.reader.view.CustomWebView
import org.eclipse.egit.github.core.Issue
import org.eclipse.egit.github.core.client.GitHubClient
import org.eclipse.egit.github.core.service.IssueService
import org.eclipse.egit.github.core.service.MilestoneService
import sjj.alog.Log

object JsUpdateManager {

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
                    JsConfig.saveJs(BookSource(it.source, js, it.version, it.starting, b))
                }

            }
        }
    }

}