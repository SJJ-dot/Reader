package com.sjianjun.reader.view

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import com.sjianjun.reader.R
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.custom_web_view.view.*
import kotlinx.android.synthetic.main.web_view.view.*
import sjj.alog.Log
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

/*
 * Created by shen jian jun on 2020-07-10
 */
class CustomWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val lifecycleObserver by lazy { LifecycleObserver(this) }
    private var webView: WebView? = null
    private var url: String? = null
    private var clearHistory: Boolean = false
    var adBlockJs: String? = null
    var adBlockUrl: List<Regex>? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.custom_web_view, this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun hasFocus(): Boolean {
        return true
    }

    fun init(lifecycle: Lifecycle) {
        web_view_stub?.inflate()
        webView = web_view
        initWebView(webView)
        initNavigation()
        lifecycle.addObserver(lifecycleObserver)
    }

    fun loadUrl(url: String, clearHistory: Boolean = false) {
        this.url = url
        this.clearHistory = clearHistory
        webView?.loadUrl(url)
    }

    private fun initWebView(webView: WebView?) {
        initWebViewSetting(webView)

        webView?.webViewClient = object : WebViewClient() {
            var started = false
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                Log.i("$url webView:${view}")
                if (url.startsWith("http")) {
                    view?.loadUrl(url)
                } else {
                    try {
                        startActivity(context, Intent(Intent.ACTION_VIEW, request.url), null)
                    } catch (e: Exception) {
                        return false
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                started = true
                Log.i(url + "started:$started webView:${view}")



                progress_bar.animFadeIn()

                val adBlockJs = adBlockJs
                if (!adBlockJs.isNullOrBlank()) {
                    webView?.evaluateJavascript(adBlockJs, null)
                }

                refresh.isSelected = true
            }

            override fun onPageFinished(webView: WebView?, url: String?) {
                Log.i(url + " started:$started UA ${webView?.settings?.userAgentString} webView:${webView} ")
                if (started) {
                    started = false

                    //不是重定向
                    val adBlockJs = adBlockJs
                    if (!adBlockJs.isNullOrBlank()) {
                        webView?.evaluateJavascript(adBlockJs, null)
                        webView?.post {
                            webView?.evaluateJavascript(adBlockJs) {
                                Log.i("adBlockJs result:$it")
                            }
                        }
                    }
                }
                forward.isEnabled = webView?.canGoForward() == true
                backward.isEnabled = webView?.canGoBack() == true
                refresh.isSelected = false
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)

                if (clearHistory) {
                    clearHistory = false
                    webView?.clearHistory()
                }
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                Log.i(url + " webView:${view}")
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val path = request.url.path
                val url = request.url.toString()
                val block = adBlockUrl?.firstOrNull {
                    it.matches(url)
                }
                if (block != null) {
//                    Log.e("${request.method} $url webView:${view}")
                    return WebResourceResponse(null, null, null)
                }
                if (path?.endsWith(".gif") == true ||
                    path?.endsWith(".js") == true ||
                    url.contains("sdk", true)
                ) {
                    Log.e("${request.method} ${request.url} webView:${view}")
                } else {
                    Log.i("${request.method} ${request.url} webView:${view}")
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        webView?.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                Log.i("progress:${newProgress} webView:${view}")
                progress_bar.progress = newProgress
                if (newProgress == 100) {
                    progress_bar.animFadeOut()
                }
                val adBlockJs = adBlockJs
                if (!adBlockJs.isNullOrBlank()) {
                    webView?.evaluateJavascript(adBlockJs, null)
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                Log.i("title:${title} webView:${view}")
//                setTitle(title)
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                Log.i("message:${message} $url webView:${view}")
                return false
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                Log.i("message:${message} $url webView:${view}")
                return super.onJsConfirm(view, url, message, result)
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                Log.i(
                    "message:${message} defaultValue:$defaultValue $url webView:${view}"
                )
                return super.onJsPrompt(view, url, message, defaultValue, result)
            }
        }
    }

    private fun initWebViewSetting(webView: WebView?) {
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
        webView.scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        webView.isScrollbarFadingEnabled = false
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val uri = Uri.parse(url)
            act?.also {
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

    /**
     * 底部导航按钮设置
     */
    private fun initNavigation() {
        home.setOnClickListener {
            loadUrl(url ?: return@setOnClickListener)
        }
        refresh.setOnClickListener {
            if (it.isSelected) {
                webView?.stopLoading()
            } else {
                webView?.reload()
            }
        }
        forward.isEnabled = false
        forward.setOnClickListener {
            webView?.goForward()
        }
        backward.isEnabled = false
        backward.setOnClickListener {
            webView?.goBack()
        }
        //切换移动版 桌面版本
        mobile.setOnClickListener {
            mobile.isSelected = !mobile.isSelected
            if (mobile.isSelected) {
                webView?.settings?.userAgentString = WEB_VIEW_UA_DESKTOP
            } else {
                webView?.settings?.userAgentString = WEB_VIEW_UA_ANDROID
            }
            webView?.reload()
        }
    }

    fun onBackPressed(): Boolean {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
            return true
        }
        return false
    }

    class LifecycleObserver(val customWebView: CustomWebView) :
        androidx.lifecycle.LifecycleObserver {

        private val webView
            get() = customWebView.webView


        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        fun onStart() {
            webView?.resumeTimers()
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        fun onResume() {
            webView?.onResume()
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        fun onPause() {
            webView?.onPause()
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        fun onStop() {
            webView?.pauseTimers()
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun onDestroy() {
            val parent = webView?.parent as? ViewGroup
            parent?.removeView(webView)
            webView?.destroy()
        }


    }


}