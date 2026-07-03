package com.sjianjun.reader.module.about

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Debug
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.MainFragmentAboutBinding
import com.sjianjun.reader.module.main.PrivacyPolicyDialogFragment
import com.sjianjun.reader.mqtt.OnlineInfos
import com.sjianjun.reader.mqtt.formatDuration
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.AppInfoUtil
import com.sjianjun.reader.utils.checkUpdate
import com.sjianjun.reader.utils.dp2Px
import com.sjianjun.reader.utils.toast
import com.sjianjun.reader.view.click
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sjj.alog.Log
import java.io.File

class AboutFragment : BaseAsyncFragment() {
    private val apkUrl: String
        get() = globalConfig.releasesInfo?.downloadApkUrl?.takeIf { it.isNotBlank() } ?: ""

    override fun getLayoutRes() = R.layout.main_fragment_about

    @SuppressLint("SetTextI18n")
    override val onLoadedView: (View) -> Unit = {
        setHasOptionsMenu(true)
        MainFragmentAboutBinding.bind(it).apply {
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
            if (globalConfig.autoCheckUpdate.value == true) {
                versionCode.performClick()
            }
            tvPrivacyPolicy.click {
                PrivacyPolicyDialogFragment().show(childFragmentManager, "PrivacyPolicyDialogFragment")
            }
            lifecycleScope.launch { OnlineInfos.refresh() }
            OnlineInfos.onlineInfo.observe(viewLifecycleOwner) { info ->
                tvOnline.text = "今日阅读时长：${info?.today_online_seconds?.formatDuration() ?: "--"}\n" +
                        "书友在线：${info.online_count}"
            }
            tvOnline.click {
                NavHostFragment.findNavController(this@AboutFragment).navigate(R.id.onlineLeaderboardFragment)
            }

            // 定时刷新 CPU/内存占用到 tvCpuInfo
            val cpuInfoJob = lifecycleScope.launch {
                var lastJiffies = readCpuJiffies()
                var lastTime = System.currentTimeMillis()
                while (isActive) {
                    delay(2000)
                    val now = System.currentTimeMillis()
                    val jiffies = readCpuJiffies()
                    // CPU% = Δ(jiffies)/100 / Δ(seconds) * 100
                    val dtSec = (now - lastTime) / 1000.0
                    val cpuPercent = if (dtSec > 0 && lastJiffies > 0) {
                        ((jiffies - lastJiffies).toDouble() / 100.0 / dtSec * 100).toFloat().coerceAtLeast(0f)
                    } else 0f
                    lastJiffies = jiffies
                    lastTime = now

                    val memKb = Debug.MemoryInfo().let { info ->
                        Debug.getMemoryInfo(info)
                        info.totalPrivateDirty.toLong()
                    }
                    val memText = if (memKb >= 1024) "%.1f MB".format(memKb / 1024.0) else "$memKb KB"
                    tvCpuInfo.text = "CPU：${"%.1f".format(cpuPercent)}%\n内存：$memText"
                }
            }
        }
    }

    /** 读取 /proc/self/stat 的 utime+stime（jiffies） */
    private fun readCpuJiffies(): Long {
        return try {
            val stat = java.io.File("/proc/self/stat").readText().trim()
            val fields = stat.substringAfterLast(')').split(Regex("\\s+"))
            val utime = fields.getOrElse(11) { "0" }.toLong()
            val stime = fields.getOrElse(12) { "0" }.toLong()
            utime + stime
        } catch (e: Exception) {
            0L
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
            Log.e(e, e)
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
            versionCode.text = "当前版本：${AppInfoUtil.versionName()} • 检查更新"
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
