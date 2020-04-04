package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.View
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.R
import kotlinx.android.synthetic.main.main_fragment_about.*

class AboutFragment : BaseFragment() {
    override fun getLayoutRes() = R.layout.main_fragment_about
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        versionCode.text = "版本号：${BuildConfig.VERSION_NAME}"
    }
}