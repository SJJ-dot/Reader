package com.sjianjun.reader.module.bookcity

import android.view.View
import androidx.core.view.GravityCompat
import androidx.lifecycle.Observer
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.coroutine.launchIo
import com.sjianjun.reader.coroutine.withMain
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import kotlinx.android.synthetic.main.bookcity_fragment_browser.*


class BrowserBookCityFragment : BaseAsyncFragment() {
    private var source: String? = null

    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_browser
    }

    override val onLoadedView: (View) -> Unit = {
        custom_web_view.init(viewLifecycleOwner.lifecycle)
        custom_web_view.adBlockUrl = globalConfig.adBlockUrlSet.map { Regex("\\A$it.*") }
        setOnBackPressed {
            when {
                drawer_layout.isDrawerOpen(GravityCompat.END) -> {
                    drawer_layout.closeDrawer(GravityCompat.END)
                    true
                }

                else -> {
                    custom_web_view.onBackPressed()
                }
            }
        }

        childFragmentManager
            .beginTransaction()
            .add(R.id.bookcity_station_list_menu, BookCityStationListFragment())
            .commitAllowingStateLoss()
        //QQ登录
        globalConfig.qqAuthLoginUri.observe(viewLifecycleOwner, Observer {
            val url = it?.toString() ?: return@Observer
            custom_web_view.loadUrl(url, true)
        })
        globalConfig.bookCityDefaultSource.observe(this, Observer {
            drawer_layout.closeDrawer(GravityCompat.END)
            source = it
            initData()
        })

    }

    private fun initData() {
        val source = source ?: return
        launchIo {
            val sourceJs = DataManager.getJavaScript(source)
            custom_web_view.adBlockJs = sourceJs?.adBlockJs
            val hostUrl = sourceJs?.execute<String>("hostUrl;") ?: ""
            withMain {
                custom_web_view?.loadUrl(hostUrl)
                activity?.supportActionBar?.title = sourceJs?.source
            }
        }
    }


}