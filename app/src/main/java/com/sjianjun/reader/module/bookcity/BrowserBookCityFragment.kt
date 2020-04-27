package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.iterator
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.utils.JS_SOURCE
import kotlinx.coroutines.flow.first

class BrowserBookCityFragment : BaseFragment() {
    private var javaScriptList: List<JavaScript> = emptyList()
    private lateinit var source: String
    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_browser
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        source = arguments?.getString(JS_SOURCE) ?: globalConfig.bookCityDefaultSource
        initMenu()
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

    }

}