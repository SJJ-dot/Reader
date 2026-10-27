package com.sjianjun.reader.module.shelf

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.FragmentWebShelfBinding
import com.sjianjun.reader.databinding.ItemBrowserTabBinding
import com.sjianjun.reader.module.bookcity.BrowserTabs
import com.sjianjun.reader.module.bookcity.WebViewBrowserActivity
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.view.click

/**
 * 原“网页书架”标签改为展示浏览器标签页列表：
 * 复用 BrowserTabs 数据，自动定位当前活跃标签，点击切换并回到浏览器，可关闭标签、新建标签。
 */
class WebShelfFragment : BaseFragment() {

    private var binding: FragmentWebShelfBinding? = null
    private val bookShelfTitle = BookShelfTitle()
    private val adapter = Adapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_web_shelf, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentWebShelfBinding.bind(view)
        setHasOptionsMenu(true)
        binding?.recycleView?.layoutManager = GridLayoutManager(requireContext(), 2)
        binding?.recycleView?.adapter = adapter
        binding?.fab?.click {
            // 新建标签页：打开浏览器首页
            WebViewBrowserActivity.startActivity(requireContext(), url = globalConfig.bookCityUrl)
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
        bookShelfTitle.destroyTileView()
        binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        // 与书架“书源”tab 共用同一套分段标题，这里选中“标签”
        bookShelfTitle.initTileView(this)
        bookShelfTitle.showSourceData(false)
    }

    private fun refresh() {
        if (BrowserTabs.tabs.isEmpty()) BrowserTabs.restore()
        val list = BrowserTabs.tabs.toList()
        adapter.submit(list)
        val active = BrowserTabs.activeIndex
        if (active in list.indices) {
            binding?.recycleView?.post { binding?.recycleView?.scrollToPosition(active) }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        private val data = mutableListOf<BrowserTabs.Tab>()

        fun submit(list: List<BrowserTabs.Tab>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(ItemBrowserTabBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val tab = data[position]
            val active = position == BrowserTabs.activeIndex
            holder.binding.tvTitle.text = tab.title.ifBlank { "无标题" }
            holder.binding.tvUrl.text = tab.url
            holder.binding.root.strokeWidth = if (active) 3 else 0
            holder.binding.root.strokeColor =
                ContextCompat.getColor(holder.itemView.context, R.color.colorPrimary)
            holder.binding.root.click {
                BrowserTabs.activeIndex = position
                // 写入待切换，浏览器回到前台（onResume，已可见）时切到该标签并加载
                BrowserTabs.pendingSwitchTabId = tab.id.toString()
                BrowserTabs.pendingSwitchUrl = tab.url
                BrowserTabs.persist()
                // 回到浏览器并恢复标签列表，定位到当前选择
                WebViewBrowserActivity.startActivity(
                    requireContext(), url = tab.url, restoreTabs = true
                )
            }
            holder.binding.btnClose.click {
                val idx = BrowserTabs.tabs.indexOf(tab).takeIf { it >= 0 } ?: position
                BrowserTabs.tabs.removeAt(idx)
                if (BrowserTabs.activeIndex >= BrowserTabs.tabs.size) {
                    BrowserTabs.activeIndex = (BrowserTabs.tabs.size - 1).coerceAtLeast(0)
                }
                BrowserTabs.persist()
                refresh()
            }
        }

        inner class Holder(val binding: ItemBrowserTabBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
