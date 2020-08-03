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
import sjj.alog.Log
import sjj.novel.util.fromJson
import sjj.novel.util.gson

class AboutFragment : BaseAsyncFragment() {

    override fun getLayoutRes() = R.layout.main_fragment_about

    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        declare.text = getString(R.string.about_app, URL_REPO)
        versionCode.setOnClickListener {
            launch(singleCoroutineKey = "checkUpdate") {
                //检查bugly更新
                val appUpgradeInfo = Beta.getAppUpgradeInfo()
                if (appUpgradeInfo == null || appUpgradeInfo.versionCode <= BuildConfig.VERSION_CODE) {
                    checkUpdate(requireActivity() as BaseActivity, true)
                    setVersionInfo()
                } else {
                    toast("已经是最新版了")
                }
            }
        }

        setVersionInfo()

        setCode()
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
                    "小说app下载链接：${downloadUrl()}"
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
        val image = ZXingUtils.createQRImage(
            downloadUrl(),
            150.dp2Px,
            150.dp2Px,
            R.color.dn_text_color_black.color(context),
            R.color.dn_background.color(context)
        )
        code.setImageBitmap(image)

    }

    private fun setVersionInfo() {


        val appUpgradeInfo = Beta.getAppUpgradeInfo()
        val newestVersion = if (appUpgradeInfo != null &&
            appUpgradeInfo.versionCode > BuildConfig.VERSION_CODE
        ) {
            appUpgradeInfo.versionName
        } else {
            val releasesInfo: ReleasesInfo? = gson.fromJson(globalConfig.releasesInfo)
            releasesInfo?.tag_name?:BuildConfig.VERSION_NAME
        }



        if (newestVersion != null) {
            versionCode.text =
                "当前版本：${BuildConfig.VERSION_NAME} | 最新版：${newestVersion}"
        } else {
            versionCode.text = "当前版本：${BuildConfig.VERSION_NAME}"
        }
    }

    private fun downloadUrl(): String {
        Log.e(gson.toJson(Beta.getAppUpgradeInfo()))
        Log.e(gson.toJson(Beta.getUpgradeInfo()))
        var downloadUrl = Beta.getAppUpgradeInfo()?.apkUrl
        if (downloadUrl.isNullOrBlank()) {
            val releaseInfo = gson.fromJson<ReleasesInfo>(globalConfig.releasesInfo)
            downloadUrl = releaseInfo?.apkDownloadUrl ?: URL_RELEASE_DEF
        }
        return downloadUrl
    }

}