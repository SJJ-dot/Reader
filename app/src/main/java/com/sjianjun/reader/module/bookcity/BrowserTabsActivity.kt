package com.sjianjun.reader.module.bookcity

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ActivityBrowserTabsBinding
import com.sjianjun.reader.databinding.ItemBrowserTabBinding

/** 标签页管理界面（Edge 风格网格） */
class BrowserTabsActivity : BaseActivity() {

    private val binding by lazy { ActivityBrowserTabsBinding.inflate(layoutInflater) }
    private val adapter = TabsAdapter()
    private var keyword = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.rvTabs.layoutManager = GridLayoutManager(this, 2)
        binding.rvTabs.adapter = adapter
        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                keyword = s?.toString()?.trim().orEmpty()
                refresh()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.btnNewTab.setOnClickListener {
            BrowserTabs.onNew?.invoke()
            finish()
        }
        refresh()
    }

    private fun refresh() {
        val list = BrowserTabs.tabs.filter {
            keyword.isBlank() ||
                    it.title.contains(keyword, true) ||
                    it.url.contains(keyword, true)
        }
        // 按最近使用排序，当前标签排最前
        adapter.submit(list.sortedByDescending { it.lastUsedAt })
    }

    private inner class TabsAdapter : RecyclerView.Adapter<TabsAdapter.Holder>() {
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
            val index = BrowserTabs.tabs.indexOf(tab)
            holder.binding.ivThumb.setImageBitmap(
                tab.thumbPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            )
            holder.binding.tvTitle.text = tab.title.ifBlank { "无标题" }
            holder.binding.tvUrl.text = tab.url
            val active = index == BrowserTabs.activeIndex
            holder.binding.root.strokeWidth = if (active) 3 else 0
            holder.binding.root.strokeColor = ContextCompat.getColor(
                holder.itemView.context, R.color.colorPrimary
            )
            holder.binding.root.setOnClickListener {
                BrowserTabs.onSelect?.invoke(index)
                finish()
            }
            holder.binding.btnClose.setOnClickListener {
                BrowserTabs.onClose?.invoke(index)
                refresh()
            }
        }

        inner class Holder(val binding: ItemBrowserTabBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
