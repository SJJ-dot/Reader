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
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.module.update.checkUpdate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.URL_RELEASE_DEF
import com.sjianjun.reader.utils.URL_REPO
import kotlinx.android.synthetic.main.main_fragment_about.*
import sjj.novel.util.fromJson
import sjj.novel.util.gson

class AboutFragment : BaseFragment() {
    private val downloadUrl = URL_RELEASE_DEF

    override fun getLayoutRes() = R.layout.main_fragment_about
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        declare.text = getString(R.string.about_app, URL_REPO)
        versionCode.setOnClickListener {
            launch {
                val releasesInfo = checkUpdate(activity!!, true)
                setVersionInfo(releasesInfo)
            }
        }
        setVersionInfo(gson.fromJson(globalConfig.releasesInfo))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_about_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.share -> {
                val releaseInfo = gson.fromJson<ReleasesInfo>(globalConfig.releasesInfo)
                val sendIntent = Intent(Intent.ACTION_SEND)
                sendIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    "小说app下载链接：${releaseInfo?.apkDownloadUrl ?: downloadUrl}"
                )
                sendIntent.type = "text/plain"
                val shareIntent = Intent.createChooser(sendIntent, "把app分享给别人")
                startActivity(shareIntent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun setVersionInfo(releasesInfo: ReleasesInfo?) {
        val download = releasesInfo?.apkAssets
        if (download != null) {
            versionCode.text =
                "当前版本：${BuildConfig.VERSION_NAME}\n最新版：${releasesInfo.tag_name} | 下载次数：${download?.download_count}"
        } else {
            versionCode.text = "当前版本：${BuildConfig.VERSION_NAME}"
        }
    }

}