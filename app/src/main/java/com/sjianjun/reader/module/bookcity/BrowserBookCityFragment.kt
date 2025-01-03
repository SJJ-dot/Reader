package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey.WEB_NEW_URL
import com.sjianjun.reader.preferences.globalConfig


class BrowserBookCityFragment : BaseFragment() {

    override fun getLayoutRes(): Int {
        return R.layout.bookcity_fragment_browser
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        childFragmentManager.beginTransaction()
            .replace(R.id.web_view_container, BookCityPageFragment.newInstance(globalConfig.bookCityUrl))
            .commitAllowingStateLoss()

        EventBus.observe<String>(WEB_NEW_URL, viewLifecycleOwner) {
            childFragmentManager.beginTransaction()
                .setMaxLifecycle(childFragmentManager.fragments.last(), Lifecycle.State.STARTED)
                .add(R.id.web_view_container, BookCityPageFragment.newInstance(it))
                .addToBackStack(null)
                .commitAllowingStateLoss()
        }
    }
}