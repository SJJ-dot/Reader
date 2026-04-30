package com.sjianjun.reader.module.about

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import androidx.core.content.FileProvider
import sjj.alog.Log
import java.io.File

class AboutFragment : BaseAsyncFragment() {
    private val apkUrl: String
        get() = globalConfig.releasesInfo?.downloadApkUrl?.takeIf { it.isNotBlank() }
            ?: "https://www.pgyer.com/SJJ-dot-reader"

    override fun getLayoutRes() = R.layout.main_fragment_about

    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        MainFragmentAboutBinding.bind(it).apply {
            declare.text = getString(R.string.about_app, URL_REPO)
            renderQrCode(code)
            code.setOnLongClickListener {
                shareQrCode()
                true
            }
            versionCode.click {
                launch(singleCoroutineKey = "checkUpdate") {
                    checkUpdate(requireActivity())
                    setVersionInfo(versionCode)
                    renderQrCode(code)
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
                    "小说app下载链接：${apkUrl}"
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

    private fun renderQrCode(imageView: ImageView) {
        imageView.post {
            runCatching {
                imageView.setImageBitmap(createQrBitmap(apkUrl, 150.dp2Px))
            }.onFailure {
                Log.e(it, it)
            }
        }
    }

    private fun shareQrCode() {
        runCatching {
            val context = requireContext()
            val qrFile = File(context.cacheDir, "share/qrcode.png").apply {
                parentFile?.mkdirs()
            }
            qrFile.outputStream().use { output ->
                createQrBitmap(apkUrl, 512).compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                qrFile,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                clipData = ClipData.newUri(context.contentResolver, "小说app下载二维码", uri)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "小说app下载二维码")
                putExtra(Intent.EXTRA_TITLE, "小说app下载二维码")
                putExtra(Intent.EXTRA_TEXT, "小说app下载链接：$apkUrl")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val resInfos = context.packageManager.queryIntentActivities(shareIntent, 0)
            for (ri in resInfos) {
                context.grantUriPermission(
                    ri.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val chooserIntent = Intent.createChooser(shareIntent, "转发二维码").apply {
                clipData = shareIntent.clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(chooserIntent)
        }.onFailure {
            Log.e(it, it)
            toast("转发二维码失败：${it.message ?: "未知错误"}")
        }
    }

    private fun createQrBitmap(content: String, size: Int): Bitmap {
        val bitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1,
            )
        )
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

}