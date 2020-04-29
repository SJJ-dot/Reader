package com.sjianjun.reader.module.bookcity

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.*
import androidx.core.view.iterator
import androidx.navigation.fragment.findNavController
import com.sjianjun.reader.BaseBrowserFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.JS_SOURCE
import com.sjianjun.reader.utils.withMain
import kotlinx.android.synthetic.main.bookcity_fragment_browser.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import sjj.alog.Log


class BrowserBookCityFragment : BaseBrowserFragment() {
    private var javaScriptList: List<Pair<JavaScript, String>> = emptyList()
    private lateinit var source: String
    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_browser
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        source = arguments?.getString(JS_SOURCE) ?: globalConfig.bookCityDefaultSource
        initMenu()

        onBackPressed = {
            if (web_view.canGoBack()) {
                web_view.goBack()
            } else {
                findNavController().popBackStack()
            }
        }

        initWebView(web_view)
        initData()
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
        activity?.supportActionBar?.title = title ?: return
//        activity?.supportActionBar?.hide()
    }

    private fun initData() {
        val sourceJs = javaScriptList.find { it.first.source == source }
        setTitle(sourceJs?.first?.source)
        web_view.loadUrl(sourceJs?.second ?: "https://m.qidian.com/")
    }

    private fun initWebView(webView: WebView) {
        setWebView(web_view)
        web_view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                Log.i(url + " webView:${view}")
                view.loadUrl(url)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                Log.i(url + " webView:${view}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(url + " webView:${view}")
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
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                Log.i("progress:${newProgress} webView:${view}")
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                Log.i("title:${title} webView:${view}")
                setTitle(title)
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