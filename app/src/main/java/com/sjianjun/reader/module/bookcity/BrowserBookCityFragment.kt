package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.BookcityFragmentBrowserBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.view.CustomWebView


class BrowserBookCityFragment : BaseFragment() {

    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_browser
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        BookcityFragmentBrowserBinding.bind(view).apply {
            customWebView.init(viewLifecycleOwner.lifecycle)
            setOnBackPressed { customWebView.onBackPressed() }

            //QQ登录
            globalConfig.qqAuthLoginUri.observe(viewLifecycleOwner, Observer {
                val url = it?.toString() ?: return@Observer
                customWebView.loadUrl(url, true)
            })
            initData(customWebView)
        }

    }

    private fun initData(customWebView: CustomWebView) {
        customWebView.loadUrl(globalConfig.bookCityUrl, true)
    }

}