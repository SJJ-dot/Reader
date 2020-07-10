package com.sjianjun.reader

import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.view.View.SCROLLBARS_OUTSIDE_OVERLAY
import android.view.ViewGroup
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.sjianjun.reader.utils.WEB_VIEW_UA_ANDROID
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

abstract class BaseBrowserFragment : BaseAsyncFragment() {
    private var webView: WebView? = null
    protected fun initWebviewSetting(webView: WebView?) {
        this.webView = webView ?: return
        WebView.setWebContentsDebuggingEnabled(true)
//声明WebSettings子类
        val webSettings = webView.settings;
        webSettings.userAgentString = WEB_VIEW_UA_ANDROID
        webSettings.javaScriptEnabled = true

//设置自适应屏幕，两者合用
        webSettings.setUseWideViewPort(true); //将图片调整到适合webview的大小
        webSettings.setLoadWithOverviewMode(true); // 缩放至屏幕的大小

//缩放操作
        webSettings.setSupportZoom(true); //支持缩放，默认为true。是下面那个的前提。
        webSettings.builtInZoomControls = true; //设置内置的缩放控件。若为false，则该WebView不可缩放
        webSettings.displayZoomControls = false; //隐藏原生的缩放控件
        webView.scrollBarStyle = SCROLLBARS_OUTSIDE_OVERLAY
        webView.isScrollbarFadingEnabled = false
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val uri = Uri.parse(url)
            activity?.also {
                AlertDialog.Builder(it)
                    .setTitle("是否允许下载文件 ${contentDisposition ?: ""}？")
                    .setMessage("文件大小：${DecimalFormat("0.##").format(contentLength.toFloat() / (1024 * 1024))}M")
                    .setPositiveButton("下载") { dialog, which ->
                        val fileName = uri.lastPathSegment ?: SimpleDateFormat(
                            "yyyy-MM-dd_HH-mm-ss",
                            Locale.getDefault()
                        )
                        val service =
                            ContextCompat.getSystemService(it, DownloadManager::class.java);
                        service?.enqueue(
                            DownloadManager.Request(uri)
                                .setMimeType(mimetype)
                                .setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    "${it.packageName}/${fileName}"
                                )
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        )
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        webView?.resumeTimers()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        webView?.pauseTimers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val parent = webView?.parent as? ViewGroup
        parent?.removeView(webView)
        webView?.destroy()
    }

    fun injectJquery(webView: WebView?) {
        webView ?: return
//        webView.evaluateJavascript(
//            """
//            //${DataManager.jquery}
//            console.log("注入的jquery 对象 "+${'$'})
//            //var inJQuery = $.noConflict(true)
//            //console.log("注入的jquery 对象 "+inJQuery)
//        """.trimIndent()
//        ) {
//            Log.i("注入jquery 变量名：inJQuery")
//        }
    }

}