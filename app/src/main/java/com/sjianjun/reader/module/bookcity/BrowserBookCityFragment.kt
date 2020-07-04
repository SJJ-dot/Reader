package com.sjianjun.reader.module.bookcity

import android.content.Intent
import android.graphics.Bitmap
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.iterator
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.sjianjun.reader.BaseBrowserFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.coroutine.launchIo
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.JS_SOURCE
import com.sjianjun.reader.utils.withMain
import kotlinx.android.synthetic.main.bookcity_fragment_browser.*
import kotlinx.coroutines.flow.first
import sjj.alog.Log


class BrowserBookCityFragment : BaseBrowserFragment() {
    private var javaScriptList: List<Pair<JavaScript, String>> = emptyList()
    private lateinit var source: String
    private var javaScript: JavaScript? = null
    private val adBlockUrl by lazy { globalConfig.adBlockUrlSet }
    private var webView: WebView? = null
    private var clearHistory = false

    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_browser
    }

    override val onLoadedView: (View) -> Unit = {
        if (webView == null) {
            webView = WebView(context)
            webView?.layoutParams = ConstraintLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            browser_book_city_root.addView(webView, 0)
        }
        onBackPressed = {
            if (webView?.canGoBack() == true) {
                webView?.goBack()
            } else {
                findNavController().popBackStack()
            }
        }

        source = arguments?.getString(JS_SOURCE) ?: globalConfig.bookCityDefaultSource
        initMenu()

        initWebView(webView)
//        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
    }

    private fun initMenu() {
        launchIo {
            val javaScriptList = DataManager.getAllJavaScript().first()
            this@BrowserBookCityFragment.javaScriptList =
                javaScriptList.filter { it.enable }.mapNotNull {
                    try {
                        val url = it.execute<String>("baseUrl;")!!
                        it to url
                    } catch (throwable: Throwable) {
                        null
                    }
                }
            withMain {
                initData()
                setHasOptionsMenu(true)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        javaScriptList.forEachIndexed { index, javaScript ->
            menu.add(0, index, index, javaScript.first.source)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.iterator().forEach {
            it.isVisible = source != it.title
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val js = javaScriptList.getOrNull(item.itemId) ?: return super.onOptionsItemSelected(item)
        source = js.first.source
        initData()
        return true
    }

    private fun setTitle(title: CharSequence?) {
        activity?.supportActionBar?.title = title ?: return
//        activity?.supportActionBar?.hide()
    }

    private fun initData() {
        val sourceJs = javaScriptList.find { it.first.source == source }
        javaScript = sourceJs?.first
        setTitle(sourceJs?.first?.source)
        clearHistory = true
        webView?.loadUrl(sourceJs?.second ?: "https://m.qidian.com/")
        if (sourceJs != null) {
            globalConfig.bookCityDefaultSource = sourceJs.first.source
        }
    }

    private fun initWebView(webView: WebView?) {
        initWebviewSetting(webView)
        globalConfig.qqAuthLoginUri.observe(viewLifecycleOwner, Observer {
            val url = it?.toString() ?: return@Observer
            clearHistory = true
            webView?.loadUrl(url)
        })
        webView?.setOnLongClickListener(object : View.OnLongClickListener {
            override fun onLongClick(v: View): Boolean {
                val result = (v as WebView).hitTestResult
                    ?: return false
                Log.e("${result.type} ${result.extra} $result")
                return false
            }
        })

        webView?.webViewClient = object : WebViewClient() {
            var started = false
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest
            ): Boolean {
                Log.i("$request webView:${view}")
                if (request?.url?.toString()?.startsWith("http") == true) {
                    view?.loadUrl(request.url?.toString())
                    started = false
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
            }

            override fun onPageFinished(webView: WebView?, url: String?) {
                Log.i(url + "started:$started webView:${webView}")
                if (started) {
                    started = false
                    val adBlockJs = javaScript?.adBlockJs
                    if (!adBlockJs.isNullOrBlank()) {
                        webView?.evaluateJavascript(adBlockJs) {
                            Log.e("adBlockJs result:$it")
                        }
                    }


                }
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
                Log.i("${request?.method} isForMainFrame:${request?.isForMainFrame} ${request?.url} webView:${view}")
                val block = adBlockUrl.firstOrNull {
                    request.url.toString().startsWith(it)
                }
                if (block != null) {
                    return WebResourceResponse(null, null, null)
                }

                return super.shouldInterceptRequest(view, request)
            }
        }
        webView?.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                Log.i("progress:${newProgress} webView:${view}")
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