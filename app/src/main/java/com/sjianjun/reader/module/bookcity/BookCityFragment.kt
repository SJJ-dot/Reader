package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import kotlinx.android.synthetic.main.bookcity_fragment.*

class BookCityFragment : BaseFragment() {
    private lateinit var adapter: Adapter
    override fun getLayoutRes() = R.layout.bookcity_fragment
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = Adapter(childFragmentManager)
        view_pager.adapter = adapter
        pager_indicator.viewPager = view_pager

        initPageFragment()

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