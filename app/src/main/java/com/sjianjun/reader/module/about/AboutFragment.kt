package com.sjianjun.reader.module.about

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Base64
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.module.bookcity.WebViewBrowserActivity
import com.sjianjun.reader.module.main.AppHelpActivity
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.AppInfoUtil
import com.sjianjun.reader.utils.checkUpdate
import com.sjianjun.reader.utils.toast
import org.json.JSONObject
import sjj.alog.Log
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 关于页：改用内置 H5（assets/about.html）实现，数据由原生注入，交互通过 JS 桥接回原生。
 * 使用 BaseFragment 同步 inflate，避免 AsyncView 异步加载 WebView 的兼容问题。
 */
class AboutFragment : BaseFragment() {

    private var webView: WebView? = null

    private val apkUrl: String
        get() = globalConfig.releasesInfo?.downloadApkUrl?.takeIf { it.isNotBlank() } ?: ""

    override fun getLayoutRes() = R.layout.main_fragment_about

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        val wv = view as? WebView ?: return
        webView = wv
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }
        wv.addJavascriptInterface(JsBridge(), "android")
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    WebViewBrowserActivity.startActivity(requireContext(), url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                injectData(view)
            }
        }
        wv.loadUrl("file:///android_asset/about.html")

        if (globalConfig.autoCheckUpdate.value == true) {
            launch(singleCoroutineKey = "checkUpdate") {
                checkUpdate(requireActivity())
                injectData(wv)
            }
        }
    }

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        super.onDestroyView()
    }

    /** 把 apk 下载地址、版本号、二维码、logo 注入 H5 */
    private fun injectData(target: WebView?) {
        target ?: return
        val currentVersion = AppInfoUtil.versionName()
        val latestVersion = globalConfig.releasesInfo?.lastVersion ?: ""
        val json = JSONObject().apply {
            put("version", currentVersion)
            put("latestVersion", latestVersion)
            put("declare", getString(R.string.about_app))
            put("apkUrl", apkUrl)
            logoToBase64()?.let { put("logo", "data:image/png;base64,$it") }
        }
        target.evaluateJavascript("renderAbout($json)", null)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun logoToBase64(): String? = runCatching {
        val drawable = ContextCompat.getDrawable(requireContext(), R.mipmap.ic_xue_xi) ?: return null
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmapToBase64(bitmap)
    }.getOrNull()

    /** 供 H5 调用的原生方法 */
    inner class JsBridge {
        @JavascriptInterface
        fun openFeedback() {
            requireActivity().runOnUiThread {
                WebViewBrowserActivity.startActivity(
                    requireContext(), "https://github.com/SJJ-dot/Reader/issues"
                )
            }
        }

        @JavascriptInterface
        fun openLicense() {
            requireActivity().runOnUiThread {
                WebViewBrowserActivity.startActivity(
                    requireContext(), "https://www.gnu.org/licenses/gpl-3.0.html"
                )
            }
        }

        @JavascriptInterface
        fun openHelp() {
            requireActivity().runOnUiThread {
                AppHelpActivity.startActivity(requireContext())
            }
        }

        @JavascriptInterface
        fun checkUpdate() {
            requireActivity().runOnUiThread {
                launch(singleCoroutineKey = "checkUpdate") {
                    checkUpdate(requireActivity())
                    injectData(webView)
                }
            }
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
                sendIntent.putExtra(Intent.EXTRA_TEXT, "小说app下载链接：${apkUrl}")
                sendIntent.type = "text/plain"
                val shareIntent = Intent.createChooser(sendIntent, "把app分享给别人")
                startActivity(shareIntent)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

}
