package com.sjianjun.reader.module.bookcity

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.tabs.TabLayout
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.DialogBrowserReadModeChapterListBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.utils.applyEdgeToEdgeDialogBar
import com.sjianjun.reader.utils.toast
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.novel.view.reader.page.PageStyle

/** 阅读模式章节/链接/历史底部弹窗。 */
class ChapterListDialogFragment : DialogFragment() {
    private var binding: DialogBrowserReadModeChapterListBinding? = null
    private var includeCrossDomain = false

    private val activity: BrowserReadModeActivity?
        get() = getActivity() as? BrowserReadModeActivity

    private lateinit var chapterAdapter: BrowserReadModeActivity.ChapterListAdapter
    private lateinit var hostListAdapter: BookCityPageFragment.HostListAdapter
    private lateinit var blackListAdapter: BookCityPageFragment.BlackListAdapter
    private lateinit var whiteListAdapter: BookCityPageFragment.WhiteListAdapter
    private lateinit var historyAdapter: BookCityPageFragment.HistoryListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.dialog_NoActionBar)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(true)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogBrowserReadModeChapterListBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.root.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
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
        b.chapterListIncludeCrossDomain.setOnCheckedChangeListener { _, checked ->
            includeCrossDomain = checked
            renderChapterList()
        }
        onShown()
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        val height = (resources.displayMetrics.heightPixels * 2 / 3f).toInt()
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
        window.setGravity(Gravity.BOTTOM)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.dn_background_dialog)))
        val pageStyle = PageStyle.getStyle(globalConfig.readerPageStyle.value)
        applyEdgeToEdgeDialogBar(!pageStyle.isDark)
    }

    private fun setupAdapters() {
        val act = activity ?: return
        chapterAdapter = BrowserReadModeActivity.ChapterListAdapter { link ->
            act.navigateChecked(
                url = link.url,
                baseHost = act.currentBaseHost(),
                onAllow = {
                    dismissAllowingStateLoss()
                    act.showChapter(link.url)
                },
                onBlock = { toast("链接已被阻止") },
            )
        }
        hostListAdapter = BookCityPageFragment.HostListAdapter(act.adBlock)
        blackListAdapter = BookCityPageFragment.BlackListAdapter(act.adBlock)
        whiteListAdapter = BookCityPageFragment.WhiteListAdapter(act.adBlock)
        historyAdapter = BookCityPageFragment.HistoryListAdapter().apply {
            onClick = { url ->
                act.navigateChecked(
                    url = url,
                    baseHost = null,
                    onAllow = {
                        dismissAllowingStateLoss()
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
        when (position) {
            0 -> {
                b.chapterListRv.layoutManager = FlexboxLayoutManager(requireContext())
                b.chapterListRv.adapter = chapterAdapter
                b.chapterListIncludeCrossDomain.visibility = View.VISIBLE
                renderChapterList()
            }
            1 -> {
                b.chapterListRv.layoutManager = LinearLayoutManager(requireContext())
                b.chapterListRv.adapter = hostListAdapter
                b.chapterListIncludeCrossDomain.visibility = View.GONE
            }
            2 -> {
                b.chapterListRv.layoutManager = LinearLayoutManager(requireContext())
                b.chapterListRv.adapter = blackListAdapter
                b.chapterListIncludeCrossDomain.visibility = View.GONE
            }
            3 -> {
                b.chapterListRv.layoutManager = LinearLayoutManager(requireContext())
                b.chapterListRv.adapter = whiteListAdapter
                b.chapterListIncludeCrossDomain.visibility = View.GONE
            }
            4 -> {
                b.chapterListRv.layoutManager = LinearLayoutManager(requireContext())
                b.chapterListRv.adapter = historyAdapter
                b.chapterListIncludeCrossDomain.visibility = View.GONE
                renderReadModeHistory()
            }
        }
    }

    fun refreshCurrentTab() {
        val b = binding ?: return
        when (b.chapterListTab.selectedTabPosition) {
            0 -> renderChapterList()
            4 -> renderReadModeHistory()
        }
    }

    fun onShown() {
        refreshCurrentTab()
        val act = activity ?: return
        val curUrl = act.pageLoader.curChapter?.link
        val selected = binding?.chapterListTab?.selectedTabPosition ?: 0
        if (selected == 0 && !curUrl.isNullOrBlank() && BrowserReadModeActivity.extractCache.get(curUrl) == null) {
            act.loadUrlForReadMode(curUrl)
        }
    }

    private fun renderChapterList() {
        val act = activity ?: return
        val curUrl = act.pageLoader.curChapter?.link
        val links = curUrl?.let { BrowserReadModeActivity.extractCache.get(it)?.allLinks } ?: emptyList()
        val baseHost = curUrl?.toHttpUrlOrNull()?.topPrivateDomain()
            ?: act.webBook?.url?.toHttpUrlOrNull()?.topPrivateDomain()
        val filtered = if (includeCrossDomain || baseHost == null) {
            links
        } else {
            links.filter { link ->
                val host = link.url.toHttpUrlOrNull()?.topPrivateDomain()
                host == null || host == baseHost
            }
        }
        chapterAdapter.submitList(filtered.toList())
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
