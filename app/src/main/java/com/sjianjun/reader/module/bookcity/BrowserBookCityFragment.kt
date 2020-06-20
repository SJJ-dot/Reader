package com.sjianjun.reader.module.bookcity

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.iterator
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.sjianjun.reader.BaseAsyncFragment
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
import kotlin.math.min


class BrowserBookCityFragment : BaseBrowserFragment() {
    private var javaScriptList: List<Pair<JavaScript, String>> = emptyList()
    private lateinit var source: String
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
        initData()
    }

    override val onDestroy: () -> Unit = {
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
            withMain { setHasOptionsMenu(true) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.bookcity_fragment_menu, menu)
        javaScriptList.forEachIndexed { index, javaScript ->
            menu.add(0, index, index, javaScript.first.source)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.iterator().forEach {
            it.isVisible = source != it.title
        }
        menu.findItem(R.id.bookcity_station).title = source
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val js = javaScriptList.getOrNull(item.itemId) ?: return super.onOptionsItemSelected(item)
        source = js.first.source
        initData()
        return true
    }

    private fun setTitle(title: CharSequence?) {
        activity?.supportActionBar?.title = title?.substring(0, min(title.length,5)) ?: return
//        activity?.supportActionBar?.hide()
    }

    private fun initData() {
        val sourceJs = javaScriptList.find { it.first.source == source }
        setTitle(sourceJs?.first?.source)
        clearHistory = true
        webView?.loadUrl(sourceJs?.second ?: "https://m.qidian.com/")
    }

    private fun initWebView(webView: WebView?) {
        initWebviewSetting(webView)
        globalConfig.qqAuthLoginUri.observe(viewLifecycleOwner, Observer {
            val url = it?.toString()?:return@Observer
            clearHistory = true
            webView?.loadUrl(url)
        })
        webView?.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                Log.i("$url webView:${view}")
                if (url?.startsWith("http") == true) {
                    view.loadUrl(url)
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        return false
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                Log.i(url + " webView:${view}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(url + " webView:${view}")
                if (clearHistory) {
                    clearHistory = false
                    webView?.clearHistory()
                }
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                Log.i(url + " webView:${view}")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.i("error:$error request:$request  webView:${view}")
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
//                super.onReceivedSslError(view, handler, error)
                //即使证书错误也继续加载。
//                handler?.proceed()
                handler?.cancel()
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
                return super.onJsAlert(view, url, message, result)
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