package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.iterator
import androidx.navigation.fragment.findNavController
import com.sjianjun.reader.BaseBrowserFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.JS_SOURCE
import kotlinx.android.synthetic.main.bookcity_fragment_browser.*
import kotlinx.coroutines.flow.first


class BrowserBookCityFragment : BaseBrowserFragment() {
    private var javaScriptList: List<JavaScript> = emptyList()
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
        setWebView(web_view)
        web_view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                view.loadUrl(url)
                return true
            }
        }
        initData()

    }

    private fun initMenu() {
        launch {
            javaScriptList = DataManager.getAllSupportBookcityJavaScript().first()
            setHasOptionsMenu(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.bookcity_fragment_menu, menu)
        javaScriptList.forEachIndexed { index, javaScript ->
            menu.add(0, index, index, javaScript.source)
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
        source = js.source
        initData()
        return true
    }

    private fun initData() {
        web_view.loadUrl("https://m.qidian.com/")
    }

}