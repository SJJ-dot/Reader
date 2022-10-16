package com.sjianjun.reader.module.bookcity

import android.view.View
import androidx.lifecycle.Observer
import com.sjianjun.coroutine.launch
import com.sjianjun.coroutine.withMain
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.preferences.AdBlockConfig
import com.sjianjun.reader.preferences.globalConfig
import kotlinx.android.synthetic.main.bookcity_fragment_browser.*
import sjj.alog.Log
import java.util.concurrent.ConcurrentLinkedDeque


class BrowserBookCityFragment : BaseAsyncFragment() {

    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_browser
    }

    override val onLoadedView: (View) -> Unit = {
        custom_web_view.init(viewLifecycleOwner.lifecycle)
        custom_web_view.adBlockUrl = ConcurrentLinkedDeque(AdBlockConfig.adBlockList)
//        Log.i(custom_web_view.adBlockUrl)
        setOnBackPressed { custom_web_view.onBackPressed() }

        //QQ登录
        globalConfig.qqAuthLoginUri.observe(viewLifecycleOwner, Observer {
            val url = it?.toString() ?: return@Observer
            custom_web_view.loadUrl(url, true)
        })
        initData()
    }

    private fun initData() {
        launch {
            custom_web_view?.loadUrl("https://www.qidian.com", true)
            activity?.supportActionBar?.title = "起点"
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