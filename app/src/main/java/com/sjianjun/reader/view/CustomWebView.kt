package com.sjianjun.reader.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.sjianjun.reader.WEB_VIEW_UA_ANDROID
import com.sjianjun.reader.WEB_VIEW_UA_DESKTOP
import com.sjianjun.reader.databinding.CustomWebViewBinding
import com.sjianjun.reader.http.CookieMgr
import com.sjianjun.reader.module.bookcity.BookCityPageActivity
import com.sjianjun.reader.utils.animFadeIn
import com.sjianjun.reader.utils.animFadeOut
import com.sjianjun.reader.utils.hide
import com.sjianjun.reader.utils.hideKeyboard
import com.sjianjun.reader.utils.show
import com.sjianjun.reader.utils.showKeyboard
import com.sjianjun.reader.utils.toast
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

/*
 * Created by shen jian jun on 2020-07-10
 */
class CustomWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    val binding: CustomWebViewBinding = CustomWebViewBinding.inflate(LayoutInflater.from(context), this, true)
    private val lifecycleObserver by lazy { LifecycleObserver(this) }
    private var webView: WebView? = null
    private var url: String? = null
    private var clearHistory: Boolean = false

    fun init(lifecycle: Lifecycle) {
        webView = binding.webView
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

            fun getTopDomain(url: HttpUrl): String? {
                val hostParts = url.host.split(".")
                return if (hostParts.size >= 2) {
                    hostParts.takeLast(2).joinToString(".") // 获取最后两个部分，例如 "example.com"
                } else {
                    null // 如果 URL 不包含有效的主域名部分
                }
            }

            fun isSameDomain(url1: HttpUrl?, url2: HttpUrl?): Boolean {
                if (url1 == null || url2 == null) return false
                return getTopDomain(url1) == getTopDomain(url2)
            }


            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                Log.i("$url ")
                val httpUrl = url.toHttpUrlOrNull()
                val origin = this@CustomWebView.url?.toHttpUrlOrNull()
                if (isSameDomain(httpUrl, origin)) {
                    // 启动新 Activity
                    BookCityPageActivity.startActivity(context, url)
                }
                return true  // 返回 true 表示我们自己处理这个 URL

            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                Log.i(url)

                if (!binding.editText.hasFocus()) {
                    binding.editText.setText(url)
                }
                binding.progressBar.animFadeIn()
                binding.refresh.isSelected = true
            }

            override fun onPageFinished(webView: WebView?, url: String?) {
                Log.i(url ?: return)
                if (!url.startsWith("http")) {
                    return
                }
                binding.forward.isEnabled = webView?.canGoForward() == true
                binding.backward.isEnabled = webView?.canGoBack() == true
                binding.refresh.isSelected = false
//                CookieMgr.saveFromResponse()
                val cookieManager = CookieManager.getInstance()
                val cookieStr = cookieManager.getCookie(url) ?: return
                val httpUrl = url.toHttpUrl()
                val cookie = Cookie.parse(httpUrl, cookieStr)
                cookie?.let { CookieMgr.saveFromResponse(httpUrl, mutableListOf(it)) }

                Log.e(cookieStr)
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
                binding.progressBar.progress = newProgress
                if (newProgress == 100) {
                    binding.progressBar.animFadeOut()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebViewSetting(webView: WebView?) {
        this.webView = webView ?: return
        WebView.setWebContentsDebuggingEnabled(true)
//声明WebSettings子类
        val webSettings = webView.settings
        webSettings.userAgentString = WEB_VIEW_UA_ANDROID
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
//设置自适应屏幕，两者合用
        webSettings.useWideViewPort = true //将图片调整到适合webview的大小
        webSettings.loadWithOverviewMode = true // 缩放至屏幕的大小

//缩放操作
        webSettings.setSupportZoom(true) //支持缩放，默认为true。是下面那个的前提。
        webSettings.builtInZoomControls = true //设置内置的缩放控件。若为false，则该WebView不可缩放
        webSettings.displayZoomControls = false //隐藏原生的缩放控件
        webView.scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        webView.isScrollbarFadingEnabled = false
    }

    /**
     * 底部导航按钮设置
     */
    private fun initNavigation() {
        binding.apply {
            home.setOnClickListener {
                loadUrl(url ?: return@setOnClickListener)
            }
            refresh.setOnClickListener {
                if (it.isSelected) {
                    webView.stopLoading()
                } else {
                    webView.reload()
                }
            }
            forward.isEnabled = false
            forward.setOnClickListener {
                webView.goForward()
            }
            backward.isEnabled = false
            backward.setOnClickListener {
                webView.goBack()
            }
            //切换移动版 桌面版本
            mobile.isSelected = true
            mobile.setOnClickListener {
                mobile.isSelected = !mobile.isSelected
                if (mobile.isSelected) {
                    webView.settings.userAgentString = WEB_VIEW_UA_ANDROID
                } else {
                    webView.settings.userAgentString = WEB_VIEW_UA_DESKTOP
                }
                webView.reload()
            }
        }
    }

    private fun initInputView() {
        binding.inputMask.setOnClickListener {
            binding.editText.clearFocus()
            webView?.requestFocus()
        }
        binding.inputClear.setOnClickListener {
            binding.editText.setText("")
        }

        binding.editText.doAfterTextChanged {
            if (binding.editText.hasFocus()) {
                if (it.toString().isEmpty()) {
                    binding.inputClear.hide()
                } else {
                    binding.inputClear.show()
                }
            } else {
                binding.inputClear.hide()
            }
        }

        binding.editText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                if (!binding.editText.text?.toString().isNullOrBlank()) {
                    binding.inputClear.show()
                } else {
                    binding.inputClear.hide()
                }
                binding.inputMask.show()
                v.showKeyboard()
            } else {
                binding.inputClear.hide()
                binding.inputMask.hide()
                v.hideKeyboard()
                binding.editText.setText(webView?.url)
            }
        }

        binding.editText.setOnEditorActionListener { _, actionId, _ ->
            if (EditorInfo.IME_ACTION_GO == actionId) {
                var url = binding.editText.text.toString()
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

                binding.editText.setText("")
                webView?.loadUrl(url)
                binding.editText.clearFocus()
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

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is State) {
            super.onRestoreInstanceState(state.superState)
            state.url?.let { webView?.loadUrl(it) }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = State(super.onSaveInstanceState())
        state.url = webView?.url
        return state
    }

    class State : BaseSavedState {
        var url: String? = null

        constructor(superState: Parcelable?) : super(superState)

        constructor(parcel: Parcel) : super(parcel) {
            url = parcel.readString()
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            super.writeToParcel(parcel, flags)
            parcel.writeString(url)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<State> {
            override fun createFromParcel(parcel: Parcel): State {
                return State(parcel)
            }

            override fun newArray(size: Int): Array<State?> {
                return arrayOfNulls(size)
            }
        }

    }

    class LifecycleObserver(private val customWebView: CustomWebView) :
        DefaultLifecycleObserver {

        private val webView
            get() = customWebView.webView

        override fun onPause(owner: LifecycleOwner) {
            webView?.pauseTimers()
            webView?.onPause()
        }

        override fun onResume(owner: LifecycleOwner) {
            webView?.resumeTimers()
            webView?.onResume()
        }


        override fun onDestroy(owner: LifecycleOwner) {
            val parent = webView?.parent as? ViewGroup
            parent?.removeView(webView)
            webView?.destroy()
        }
    }


}