package com.sjianjun.reader.module.about

import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.module.update.checkUpdate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.main_fragment_about.*

class AboutFragment : BaseAsyncFragment() {

    override fun getLayoutRes() = R.layout.main_fragment_about

    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        declare.text = getString(R.string.about_app, URL_REPO)
        versionCode.setOnClickListener {
            launch(singleCoroutineKey = "checkUpdate") {
                checkUpdate(requireActivity())
                setVersionInfo()
            }
        }

        setVersionInfo()
        versionCode.performClick()
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
                    "小说app下载链接：https://www.pgyer.com/SJJ-dot-reader"
                )
//                sendIntent.setClassName("com.tencent.mm","com.tencent.mm.ui.tools.ShareImgUI")
                sendIntent.type = "text/plain"
                val shareIntent = Intent.createChooser(sendIntent, "把app分享给别人")
                startActivity(shareIntent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setVersionInfo() {

        val releaseInfo = globalConfig.releasesInfo
        if (releaseInfo?.isUpgradeable() == true) {
            versionCode.text =
                "当前版本：${AppInfoUtil.versionName()}->${releaseInfo.lastVersion}"
        } else {
            versionCode.text = "当前版本：${AppInfoUtil.versionName()}"
        }
    }

}