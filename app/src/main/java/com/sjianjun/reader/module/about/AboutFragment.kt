package com.sjianjun.reader.module.about

import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.ReleasesInfo
import com.sjianjun.reader.module.update.checkUpdate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.*
import com.tencent.bugly.beta.Beta
import kotlinx.android.synthetic.main.main_fragment_about.*
import sjj.novel.util.fromJson
import sjj.novel.util.gson

class AboutFragment : BaseAsyncFragment() {

    override fun getLayoutRes() = R.layout.main_fragment_about

    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        declare.text = getString(R.string.about_app, URL_REPO)
        versionCode.setOnClickListener {
            launch {
                val releasesInfo = checkUpdate(requireActivity() as BaseActivity, true)
                setVersionInfo(releasesInfo)
                setCode()
            }
        }
        setVersionInfo(gson.fromJson(globalConfig.releasesInfo))
        setCode()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_fragment_about_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.share -> {
                var downloadUrl = Beta.getAppUpgradeInfo()?.apkUrl
                if (downloadUrl.isNullOrBlank()) {
                    val releaseInfo = gson.fromJson<ReleasesInfo>(globalConfig.releasesInfo)
                    downloadUrl = releaseInfo?.apkDownloadUrl ?: URL_RELEASE_DEF
                }
                val sendIntent = Intent(Intent.ACTION_SEND)
                sendIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    "小说app下载链接：${downloadUrl}"
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

    private fun setCode() {
        //设置二维码
        var downloadUrl = Beta.getAppUpgradeInfo()?.apkUrl
        if (downloadUrl.isNullOrBlank()) {
            val releaseInfo = gson.fromJson<ReleasesInfo>(globalConfig.releasesInfo)
            downloadUrl = releaseInfo?.apkDownloadUrl ?: URL_RELEASE_DEF
        }
        val image = ZXingUtils.createQRImage(
            downloadUrl,
            150.dp2Px,
            150.dp2Px,
            R.color.dn_text_color_black.color(context),
            R.color.dn_background.color(context)
        )
        code.setImageBitmap(image)

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