package com.sjianjun.reader.module.bookcity

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.sjianjun.reader.BaseAsyncFragment
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.repository.DataManager
import com.sjianjun.reader.repository.DataManager.pageDataStore
import com.sjianjun.reader.utils.*
import kotlinx.android.synthetic.main.bookcity_fragment.*
import kotlinx.coroutines.flow.first

class BookCityFragment : BaseAsyncFragment() {
    lateinit var source: String
    lateinit var pageId: String
    private lateinit var adapter: Adapter
    private var javaScriptList: List<JavaScript> = emptyList()
    override fun getLayoutRes() = R.layout.bookcity_fragment

    override val onCreate: BaseAsyncFragment.() -> Unit = {

        source = arguments?.getString(JS_SOURCE) ?: globalConfig.bookCityDefaultSource
        pageId = arguments?.getString(PAGE_ID) ?: ""

        adapter = Adapter(childFragmentManager)
        view_pager.adapter = adapter
        pager_indicator.viewPager = view_pager

        initMenu()

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
        pageId = ""
        initData()
        return true
    }


    private fun initData() {
        launchIo {
            withMain {
                load_state.show()
                load_state.text = "加载中…………"
            }
            val page = pageDataStore[pageId]
            val loadPage = DataManager.loadPage(page?.source ?: source, page?.pageScript ?: "")
            if (loadPage != null) {
                val pageList = loadPage.pageList
                loadPage.pageList = emptyList()

                val fragmentList = mutableListOf<FragmentBean>()

                pageDataStore[loadPage.pageId] = loadPage
                fragmentList.add(
                    FragmentBean(fragmentCreate(PAGE_ID, loadPage.pageId), loadPage.title)
                )

                fragmentList.addAll(pageList.map {
                    pageDataStore[it.pageId] = it
                    FragmentBean(fragmentCreate(PAGE_ID, it.pageId), it.title)
                })

                withMain {
                    adapter.fragmentList = fragmentList
                    view_pager.adapter = adapter
                    adapter.notifyDataSetChanged()
                }
            }

            withMain {
                if (adapter.fragmentList.isEmpty()) {
                    load_state.text = "什么都没有"
                } else {
                    load_state.hide()
                }
            }
        }

    }

    class FragmentBean(val fragment: BookCityPageFragment, val title: String)


    @SuppressLint("WrongConstant")
    class Adapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        var fragmentList: List<FragmentBean> = emptyList()
        override fun getItem(position: Int): Fragment {
            return fragmentList[position].fragment
        }

        override fun getCount(): Int {
            return fragmentList.size
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return fragmentList[position].title
        }
    }
}