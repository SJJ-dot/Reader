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
import androidx.recyclerview.widget.ItemTouchHelper
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
        attachDragSort()
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
        // 按打开顺序排序（标签列表本身即打开顺序）
        adapter.submit(list)
    }

    /** 长按拖动调整标签顺序（搜索过滤时禁用） */
    private fun attachDragSort() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = keyword.isBlank()

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (keyword.isNotBlank()) return false
                adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // 拖动结束后刷新 active 标记并落盘顺序
                adapter.notifyChanged()
                BrowserTabs.persist()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.rvTabs)
    }

    private inner class TabsAdapter : RecyclerView.Adapter<TabsAdapter.Holder>() {
        private val data = mutableListOf<BrowserTabs.Tab>()

        fun submit(list: List<BrowserTabs.Tab>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        /** 拖动调整位置：同步重排底层标签列表与展示列表 */
        fun move(from: Int, to: Int) {
            if (from == to || from !in data.indices || to !in data.indices) return
            val tabs = BrowserTabs.tabs
            val activeTab = tabs.getOrNull(BrowserTabs.activeIndex)
            val moved = tabs.removeAt(from)
            tabs.add(to, moved)
            BrowserTabs.activeIndex = tabs.indexOf(activeTab).coerceAtLeast(0)
            val item = data.removeAt(from)
            data.add(to, item)
            notifyItemMoved(from, to)
        }

        fun notifyChanged() = notifyDataSetChanged()

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
