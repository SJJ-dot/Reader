package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.DialogBrowserReadModeChapterListBinding
import com.sjianjun.reader.utils.toast

/** 阅读模式右侧抽屉：历史 / Host / 黑名单 / 白名单。 */
class ChapterListDialogFragment : Fragment() {
    private var binding: DialogBrowserReadModeChapterListBinding? = null

    private val activity: BrowserReadModeActivity?
        get() = getActivity() as? BrowserReadModeActivity

    private lateinit var hostListAdapter: BookCityPageFragment.HostListAdapter
    private lateinit var blackListAdapter: BookCityPageFragment.BlackListAdapter
    private lateinit var whiteListAdapter: BookCityPageFragment.WhiteListAdapter
    private lateinit var historyAdapter: BookCityPageFragment.HistoryListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogBrowserReadModeChapterListBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        setupAdapters()
        b.chapterListRv.itemAnimator = null
        applyTab(0)
        b.chapterListTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                applyTab(tab?.position ?: 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        onShown()
    }

    private fun setupAdapters() {
        val act = activity ?: return
        hostListAdapter = BookCityPageFragment.HostListAdapter(act.adBlock)
        blackListAdapter = BookCityPageFragment.BlackListAdapter(act.adBlock)
        whiteListAdapter = BookCityPageFragment.WhiteListAdapter(act.adBlock)
        historyAdapter = BookCityPageFragment.HistoryListAdapter().apply {
            onClick = { url ->
                act.navigateChecked(
                    url = url,
                    baseHost = null,
                    onAllow = {
                        act.closeChapterDrawer()
                        act.showChapter(url)
                    },
                    onBlock = { toast("链接已被阻止") },
                )
            }
            onDeleteHistory = { item ->
                if (item == null) {
                    act.readModeHistory.clear()
                } else {
                    act.readModeHistory.removeAll { it.url == item.url }
                }
                renderReadModeHistory()
            }
        }
        act.adBlock.hostList.observe(viewLifecycleOwner) {
            hostListAdapter.data = it
            hostListAdapter.notifyDataSetChanged()
        }
        act.adBlock.blacklist.observe(viewLifecycleOwner) {
            blackListAdapter.data = it
            blackListAdapter.notifyDataSetChanged()
        }
        act.adBlock.whitelist.observe(viewLifecycleOwner) {
            whiteListAdapter.data = it
            whiteListAdapter.notifyDataSetChanged()
        }
    }

    private fun applyTab(position: Int) {
        val b = binding ?: return
        b.chapterListRv.layoutManager = LinearLayoutManager(requireContext())
        b.chapterListRv.adapter = when (position) {
            0 -> historyAdapter.also { renderReadModeHistory() }
            1 -> hostListAdapter
            2 -> blackListAdapter
            3 -> whiteListAdapter
            else -> historyAdapter.also { renderReadModeHistory() }
        }
    }

    fun refreshCurrentTab() {
        val b = binding ?: return
        if (b.chapterListTab.selectedTabPosition == 0) {
            renderReadModeHistory()
        }
    }

    fun onShown() {
        refreshCurrentTab()
    }

    private fun renderReadModeHistory() {
        val act = activity ?: return
        historyAdapter.data = act.readModeHistory.toMutableList()
        historyAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "ChapterListDialogFragment"
    }
}
