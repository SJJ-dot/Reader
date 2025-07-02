package com.sjianjun.reader.module.about

import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.URL_REPO
import com.sjianjun.reader.databinding.MainFragmentAboutBinding
import com.sjianjun.reader.utils.checkUpdate
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.*
import com.sjianjun.reader.view.click
import sjj.alog.Log
import java.io.File

class AboutFragment : BaseAsyncFragment() {

    override fun getLayoutRes() = R.layout.main_fragment_about

    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        MainFragmentAboutBinding.bind(it).apply {
            declare.text = getString(R.string.about_app, URL_REPO)
            versionCode.click {
                launch(singleCoroutineKey = "checkUpdate") {
                    checkUpdate(requireActivity())
                    setVersionInfo(versionCode)
                }
            }

            setVersionInfo(versionCode)
            versionCode.performClick()
        }
    }

    fun exportDb() {
        val exportedDbFile = File(requireContext().externalCacheDir, "database/app_database_exported")

        try {
            exportedDbFile.parentFile?.mkdirs()
            File("DbFactory.dbFile").copyTo(exportedDbFile, true)
            Log.i("数据库已导出到${exportedDbFile.absolutePath}")
            toast("数据库已导出到${exportedDbFile.absolutePath}", Toast.LENGTH_LONG)
        } catch (e: Exception) {
            Log.e(e,e)
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

    private fun setVersionInfo(versionCode: TextView) {
        val releaseInfo = globalConfig.releasesInfo
        if (releaseInfo?.isUpgradeable() == true) {
            versionCode.text =
                "当前版本：${AppInfoUtil.versionName()}->${releaseInfo.lastVersion}"
        } else {
            versionCode.text = "当前版本：${AppInfoUtil.versionName()}"
        }
    }

}