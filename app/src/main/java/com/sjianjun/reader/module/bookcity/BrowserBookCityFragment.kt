package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import com.sjianjun.coroutine.launch
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.preferences.globalConfig
import kotlinx.android.synthetic.main.bookcity_fragment_browser.*


class BrowserBookCityFragment : BaseFragment() {

    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_browser
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        custom_web_view.init(viewLifecycleOwner.lifecycle)
        setOnBackPressed { custom_web_view?.onBackPressed() == true }

        //QQ登录
        globalConfig.qqAuthLoginUri.observe(viewLifecycleOwner, Observer {
            val url = it?.toString() ?: return@Observer
            custom_web_view.loadUrl(url, true)
        })
        initData()
    }

    private fun initData() {
        launch {
            custom_web_view?.loadUrl("https://m.qidian.com", true)
            activity?.supportActionBar?.title = "起点"
        }
    }

}