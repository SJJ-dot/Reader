package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.SEARCH_KEY
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.bundle
import kotlinx.android.synthetic.main.bookcity_fragment_browser.*


class BrowserBookCityFragment : BaseFragment() {

    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_browser
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        custom_web_view.init(viewLifecycleOwner.lifecycle) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("搜索书籍“${it}”？")
                .setMessage("点击确定去搜索页搜索本书书籍“${it}”")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    NavHostFragment.findNavController(this)
                        .navigate(R.id.searchFragment, bundle(SEARCH_KEY, it))
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        setOnBackPressed { custom_web_view?.onBackPressed() == true }

        //QQ登录
        globalConfig.qqAuthLoginUri.observe(viewLifecycleOwner, Observer {
            val url = it?.toString() ?: return@Observer
            custom_web_view.loadUrl(url, true)
        })
        initData()
    }

    private fun initData() {
        custom_web_view?.loadUrl("https://m.qidian.com", true)
        activity?.supportActionBar?.title = "起点"
    }

}