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
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import com.google.gson.annotations.Expose
import com.sjianjun.reader.R
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.custom_web_view.view.*
import kotlinx.android.synthetic.main.web_view.view.*
import sjj.alog.Log
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

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

    var adBlockUrl: ConcurrentLinkedDeque<AdBlock>? = null
    var adBlockJs: String? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.custom_web_view, this)
    }

    fun init(lifecycle: Lifecycle) {
        web_view_stub?.inflate()
        webView = web_view
        initWebViewSetting(webView)
        initWebView(webView)
        initNavigation()
        initInputView()
        lifecycle.addObserver(lifecycleObserver)
    }

    fun loadUrl(url: String, clearHistory: Boolean = false) {
        this.url = url
        this.clearHistory = clearHistory
        webView?.loadUrl(url)
    }

    private fun initWebView(webView: WebView?) {
        webView?.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                Log.i("$url ")
                if (!url.startsWith("http")) {
                    try {
                        startActivity(context, Intent(Intent.ACTION_VIEW, request.url), null)
                    } catch (e: Exception) {
                    }
                    return true
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                Log.i(url)

                if (!edit_text.hasFocus()) {
                    edit_text.setText(url)
                }

                progress_bar.animFadeIn()

                val adBlockJs = adBlockJs
                if (!adBlockJs.isNullOrBlank()) {
                    webView?.evaluateJavascript(adBlockJs, null)
                }

                refresh.isSelected = true
            }

            override fun onPageFinished(webView: WebView?, url: String?) {
                Log.i(url)

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
                Log.i("$url")
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val path = request.url.path
                val url = request.url.toString()

                val adBlockList = adBlockUrl
                val adBlock = adBlockList?.find {
                    it.regex?.matches(url) == true
                }
                if (adBlock != null) {
                    adBlockList.remove(adBlock)
                    adBlock.hitCount++
                    adBlockList.addFirst(adBlock)

                    Log.e("${request.method} $url $adBlock")
                    return WebResourceResponse(null, null, null)
                }
                if (path?.endsWith(".gif") == true ||
                    path?.endsWith(".js") == true ||
                    url.contains("sdk", true)
                ) {
                    Log.w("${request.method} ${request.url} ")
                } else {
                    Log.i("${request.method} ${request.url} ")
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        webView?.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                Log.i("progress:${newProgress} ")
                progress_bar.progress = newProgress
                if (newProgress == 100) {
                    progress_bar.animFadeOut()
                }
                val adBlockJs = adBlockJs
                if (!adBlockJs.isNullOrBlank()) {
                    webView?.evaluateJavascript(adBlockJs, null)
                }
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
                    .setTitle("是否允许下载文件 ${contentDisposition ?: uri.lastPathSegment ?: ""}？")
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

    private fun initInputView() {
        input_mask.setOnClickListener {
            edit_text.clearFocus()
            webView?.requestFocus()
        }
        input_clear.setOnClickListener {
            edit_text.setText("")
        }

        edit_text.doAfterTextChanged {
            if (edit_text.hasFocus()) {
                if (it.toString().isEmpty()) {
                    input_clear.hide()
                } else {
                    input_clear.show()
                }
            } else {
                input_clear.hide()
            }
        }

        edit_text.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                if (!edit_text.text?.toString().isNullOrBlank()) {
                    input_clear.show()
                } else {
                    input_clear.hide()
                }
                input_mask.show()
                v.showKeyboard()
            } else {
                input_clear.hide()
                input_mask.hide()
                v.hideKeyboard()
                edit_text.setText(webView?.url)
            }
        }

        edit_text.setOnEditorActionListener { v, actionId, event ->
            if (EditorInfo.IME_ACTION_GO == actionId) {
                var url = edit_text.text.toString()
                if (url.isBlank()) {
                    toast("请输入正确的URL地址")
                    return@setOnEditorActionListener true
                }
                if (!URLUtil.isValidUrl(url)) {
                    url = "http://$url"
                    if (!URLUtil.isValidUrl(url)) {
                        toast("请输入正确的URL地址")
                        return@setOnEditorActionListener true
                    }
                }

                edit_text.setText("")
                webView?.loadUrl(url)
                edit_text.clearFocus()
                webView?.requestFocus()
            }
            return@setOnEditorActionListener EditorInfo.IME_ACTION_GO == actionId
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

    data class AdBlock(val pattern: String = "") : Comparable<AdBlock> {
        var hitCount: Int = 0

        @Expose(serialize = false)
        var regex: Regex? = null
            get() {
                if (field == null) {
                    field = Regex("\\A$pattern.*")
                }
                return field
            }

        override
        fun compareTo(other: AdBlock): Int {
            return hitCount.compareTo(other.hitCount)
        }

        override fun toString(): String {
            return "{hitCount=$hitCount,regex=$regex}"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AdBlock

            if (pattern != other.pattern) return false

            return true
        }

        override fun hashCode(): Int {
            return pattern.hashCode()
        }


    }


}