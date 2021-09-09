package com.sjianjun.reader.module.bookcity

import android.view.View
import androidx.core.view.GravityCompat
import androidx.lifecycle.Observer
import com.sjianjun.coroutine.launchIo
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.preferences.AdBlockConfig
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import kotlinx.android.synthetic.main.bookcity_fragment_browser.*
import sjj.alog.Log
import java.util.concurrent.ConcurrentLinkedDeque


class BrowserBookCityFragment : BaseAsyncFragment() {
    private var source: String? = null

    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_browser
    }

    override val onLoadedView: (View) -> Unit = {
        custom_web_view.init(viewLifecycleOwner.lifecycle)
        custom_web_view.adBlockUrl = ConcurrentLinkedDeque(AdBlockConfig.adBlockList)
//        Log.i(custom_web_view.adBlockUrl)
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
            custom_web_view.adBlockJs = AdBlockConfig.getAdBlockJs(source)
            val hostUrl = sourceJs?.execute<String>("hostUrl;") ?: ""
            withMain {
                custom_web_view?.loadUrl(hostUrl, true)
                activity?.supportActionBar?.title = sourceJs?.source
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val list = custom_web_view.adBlockUrl?.toMutableList()
        if (list != null) {
            list.sortDescending()
            AdBlockConfig.adBlockList = list
            Log.i(list)
        }
    }

}