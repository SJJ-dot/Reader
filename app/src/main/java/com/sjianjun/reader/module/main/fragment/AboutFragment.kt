package com.sjianjun.reader.module.main.fragment

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.GithubApi
import com.sjianjun.reader.module.update.checkUpdate
import com.sjianjun.reader.preferences.globalConfig
import kotlinx.android.synthetic.main.main_fragment_about.*
import sjj.novel.util.fromJson
import sjj.novel.util.gson

class AboutFragment : BaseFragment() {
    private val downloadUrl =
        "https://github.com/SJJ-dot/Reader/releases/download/0.4.112/reader-master-release.112.-0.4.112.apk"
    private var githubApi: GithubApi? = null
    override fun getLayoutRes() = R.layout.main_fragment_about
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        setVersionInfo(gson.fromJson(globalConfig.releasesInfo))
        viewLaunch {
            val githubApi = checkUpdate(activity!!)
            setVersionInfo(githubApi)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_about_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.share -> {
                val sendIntent = Intent(Intent.ACTION_SEND)
                sendIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    "小说app下载链接：${githubApi?.apkDownloadUrl ?: downloadUrl}"
                )
                sendIntent.type = "text/plain"
                val shareIntent = Intent.createChooser(sendIntent, "把app分享给别人")
                startActivity(shareIntent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun setVersionInfo(githubApi: GithubApi?) {
        val download = githubApi?.apkAssets
        if (download != null) {
            versionCode.text =
                "当前版本：${BuildConfig.VERSION_NAME}\n最新版：${githubApi.tag_name} | 下载次数：${download?.download_count}"
        } else {
            versionCode.text = "当前版本：${BuildConfig.VERSION_NAME}"
        }
    }

}