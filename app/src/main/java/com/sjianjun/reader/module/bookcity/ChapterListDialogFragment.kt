package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.DialogBrowserReadModeChapterListBinding
import com.sjianjun.reader.module.bookcity.BrowserReadModeActivity.ChapterLink
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
    private lateinit var chapterAdapter: ChapterListAdapter

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
        chapterAdapter = ChapterListAdapter { item ->
            act.closeChapterDrawer()
            act.showChapter(item.url)
            act.loadUrlForReadMode(item.url)
        }
        historyAdapter = BookCityPageFragment.HistoryListAdapter().apply {
            onClick = { url ->
                act.closeChapterDrawer()
                act.showChapter(url)
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

class ChapterListAdapter(private val onClick: (ChapterLink) -> Unit) : androidx.recyclerview.widget.ListAdapter<ChapterLink, ChapterListAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<ChapterLink>() {
        override fun areItemsTheSame(oldItem: ChapterLink, newItem: ChapterLink) = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: ChapterLink, newItem: ChapterLink) = oldItem == newItem
    }
) {
    class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val textView: android.widget.TextView = view.findViewById(R.id.chapter_title)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_browser_chapter, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.textView.text = item.title
        holder.itemView.setOnClickListener { onClick(item) }
    }
}
