package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.View
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.GithubApi
import com.sjianjun.reader.module.update.checkUpdate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.CONTENT_TYPE_ANDROID
import kotlinx.android.synthetic.main.main_fragment_about.*
import sjj.novel.util.fromJson
import sjj.novel.util.gson

class AboutFragment : BaseFragment() {
    override fun getLayoutRes() = R.layout.main_fragment_about
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setVersionInfo(gson.fromJson(globalConfig.releasesInfo))
        viewLaunch {
            val githubApi = checkUpdate(activity!!)
            setVersionInfo(githubApi)
        }
    }

    private fun setVersionInfo(githubApi: GithubApi?) {
        val download = githubApi?.assets?.find { it.content_type == CONTENT_TYPE_ANDROID }
        if (download != null) {
            versionCode.text =
                "当前版本：${BuildConfig.VERSION_NAME}\n最新版：${githubApi.tag_name} | 下载次数：${download?.download_count}"
        }
    }

}