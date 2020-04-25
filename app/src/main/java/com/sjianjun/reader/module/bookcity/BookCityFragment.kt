package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import kotlinx.android.synthetic.main.bookcity_fragment.*
import sjj.alog.Log

class BookCityFragment : BaseFragment() {
    var source: String = ""
    private lateinit var adapter: Adapter
    override fun getLayoutRes() = R.layout.bookcity_fragment
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = Adapter(childFragmentManager)
        view_pager.adapter = adapter
        pager_indicator.viewPager = view_pager

        initPageFragment()

        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.bookcity_fragment_menu, menu)
        (0..3).forEach {
            menu.add(0, it + 100, it, "测试菜单：$it")
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.iterator().forEach {
            if ("测试菜单：2" == it.title) {
                it.isVisible = false
            }
        }
        menu.findItem(R.id.bookcity_station).title = "修改名字测试"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.e(item.title)
        return super.onOptionsItemSelected(item)
    }

    private fun initPageFragment() {
        adapter.fragmentList = listOf(
            FragmentBean(BookCityPageFragment(), "测试1"),
            FragmentBean(BookCityPageFragment(), "测试2"),
            FragmentBean(BookCityPageFragment(), "测试3"),
            FragmentBean(BookCityPageFragment(), "测试4"),
            FragmentBean(BookCityPageFragment(), "测试5"),
            FragmentBean(BookCityPageFragment(), "测试6")
        )
        adapter.notifyDataSetChanged()
    }

    class FragmentBean(val fragment: BookCityPageFragment, val title: String)


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