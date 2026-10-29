package com.sjianjun.reader.module.shelf

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.Fragment
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.FragmentWebShelfBinding
import com.sjianjun.reader.databinding.ItemBrowserTabBinding
import com.sjianjun.reader.module.bookcity.BrowserTabs
import com.sjianjun.reader.module.bookcity.WebViewBrowserActivity
import com.sjianjun.reader.view.click

/**
 * 原“网页书架”标签改为展示浏览器标签页列表：
 * 复用 BrowserTabs 数据，自动定位当前活跃标签，点击切换并回到浏览器，可关闭标签、新建标签。
 */
class WebShelfFragment : Fragment() {

    private var binding: FragmentWebShelfBinding? = null
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
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
        binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
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
            // 本地缩略图文件直接用 BitmapFactory 解码（与浏览器标签列表一致）；
            // 不要用 Glide 加载裸绝对路径，会被当成 URL 加载失败显示占位图
            holder.binding.ivThumb.setImageBitmap(
                tab.thumbPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            )
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
