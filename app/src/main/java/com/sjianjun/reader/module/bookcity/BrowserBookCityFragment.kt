package com.sjianjun.reader.module.bookcity

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.*
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GravityCompat
import androidx.lifecycle.Observer
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.sjianjun.reader.BaseBrowserFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.coroutine.launchIo
import com.sjianjun.reader.coroutine.withMain
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.animFadeIn
import com.sjianjun.reader.utils.animFadeOut
import com.sjianjun.reader.utils.canScrollVertically
import kotlinx.android.synthetic.main.bookcity_fragment_browser.*
import sjj.alog.Log


class BrowserBookCityFragment : BaseBrowserFragment() {
    private var source: String? = null
    private var javaScript: JavaScript? = null
    private val adBlockUrl by lazy { globalConfig.adBlockUrlSet.map { Regex("\\A$it.*") } }
    private var webView: WebView? = null
    private var clearHistory = false

    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_browser
    }

    override val onLoadedView: (View) -> Unit = {
        webView = WebView(context)
        webView?.id = R.id.web_view
        browser_book_city_root.addView(webView, 0)
        webView?.layoutParams?.apply {
            width = MATCH_PARENT
            height = MATCH_PARENT
        }

        setOnBackPressed {
            when {
                drawer_layout.isDrawerOpen(GravityCompat.END) -> {
                    drawer_layout.closeDrawer(GravityCompat.END)
                    true
                }
                webView?.canGoBack() == true -> {
                    webView?.goBack()
                    true
                }
                else -> {
                    false
                }
            }
        }

        childFragmentManager
            .beginTransaction()
            .add(R.id.bookcity_station_list_menu, BookCityStationListFragment())
            .commitAllowingStateLoss()
        //底部导航按钮设置
        initNavigation()
        initWebView(webView)
        globalConfig.bookCityDefaultSource.observe(this, Observer {
            drawer_layout.closeDrawer(GravityCompat.END)
            source = it
            initData()
        })

    }

    /**
     * 底部导航按钮设置
     */
    private fun initNavigation() {
        home.setOnClickListener {
            if (!source.isNullOrBlank()) {
                initData()
            }
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
        mobile.setOnClickListener {
            mobile.isSelected = !mobile.isSelected
        }
    }

    private fun initData() {
        val source = source ?: return
        launchIo {
            val sourceJs = DataManager.getJavaScript(source)
            javaScript = sourceJs
            clearHistory = true
            val hostUrl = sourceJs?.execute<String>("hostUrl;") ?: ""
            withMain {
                webView?.loadUrl(hostUrl)
                activity?.supportActionBar?.title = sourceJs?.source
            }
        }
    }

    private fun initWebView(webView: WebView?) {
        initWebviewSetting(webView)
        globalConfig.qqAuthLoginUri.observe(viewLifecycleOwner, Observer {
            val url = it?.toString() ?: return@Observer
            clearHistory = true
            webView?.loadUrl(url)
        })
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
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (e: Exception) {
                        return false
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                started = true
                Log.i(url + "started:$started webView:${view}")

                injectJquery(view)

                progress_bar.animFadeIn()

                val adBlockJs = javaScript?.adBlockJs
                if (!adBlockJs.isNullOrBlank()) {
                    webView?.evaluateJavascript(adBlockJs, null)
                }

                refresh.isSelected = true
            }

            override fun onPageFinished(webView: WebView?, url: String?) {
                Log.i(url + " started:$started webView:${webView}")
                if (started) {
                    started = false

                    //不是重定向
                    val adBlockJs = javaScript?.adBlockJs
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
                val block = adBlockUrl.firstOrNull {
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
                val adBlockJs = javaScript?.adBlockJs
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

}