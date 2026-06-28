package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.sjianjun.reader.databinding.DialogBrowserReadModeChapterListBinding
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.launch

class ChapterListDialogFragment : Fragment() {
    private var binding: DialogBrowserReadModeChapterListBinding? = null

    private val activity: BrowserReadModeActivity?
        get() = getActivity() as? BrowserReadModeActivity

    private lateinit var hostListAdapter: BookCityPageFragment.HostListAdapter
    private lateinit var blackListAdapter: BookCityPageFragment.BlackListAdapter
    private lateinit var whiteListAdapter: BookCityPageFragment.WhiteListAdapter
    private lateinit var historyAdapter: BookCityPageFragment.HistoryListAdapter
    private lateinit var chapterAdapter: BrowserReadModeActivity.ChapterListAdapter

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
        chapterAdapter = BrowserReadModeActivity.ChapterListAdapter { item ->
            act.navigateChecked(
                url = item.url,
                baseHost = act.currentBaseHost(),
                onAllow = {
                    act.closeChapterDrawer()
                    act.showChapter(item.url)
                    act.loadUrlForReadMode(item.url)
                },
                onBlock = { toast("链接已被阻止") },
            )
        }
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
            0 -> chapterAdapter.also { renderMappedChapterList() }
            1 -> historyAdapter.also { renderReadModeHistory() }
            2 -> hostListAdapter
            3 -> blackListAdapter
            4 -> whiteListAdapter
            else -> chapterAdapter.also { renderMappedChapterList() }
        }
    }

    fun refreshCurrentTab() {
        val b = binding ?: return
        when (b.chapterListTab.selectedTabPosition) {
            0 -> renderMappedChapterList()
            1 -> renderReadModeHistory()
        }
    }

    fun onShown() {
        refreshCurrentTab()
    }

    private fun renderMappedChapterList() {
        val act = activity ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            chapterAdapter.submitList(act.buildMappedChapterLinks())
        }
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
