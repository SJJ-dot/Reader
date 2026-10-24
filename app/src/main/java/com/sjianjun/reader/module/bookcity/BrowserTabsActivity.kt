package com.sjianjun.reader.module.bookcity

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.PopupMenu
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
    /** 当前长按弹出的菜单（拖动开始时关闭） */
    private var currentMenu: PopupMenu? = null

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
        // 自动定位到当前显示的标签
        val activeTab = BrowserTabs.tabs.getOrNull(BrowserTabs.activeIndex)
        val activePos = if (activeTab != null) list.indexOf(activeTab) else -1
        if (activePos >= 0) {
            binding.rvTabs.post { binding.rvTabs.scrollToPosition(activePos) }
        }
    }

    /** 长按立即弹菜单；开始拖动时隐藏菜单并进入排序（搜索过滤时禁用） */
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
                // 拖动开始：隐藏长按菜单
                currentMenu?.dismiss()
                currentMenu = null
                adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // 长按：立即弹出菜单（不等松手）
                    val vh = viewHolder ?: return
                    val pos = vh.bindingAdapterPosition
                    if (pos >= 0) {
                        showTabMenu(pos, vh.itemView)
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // 拖动结束后刷新 active 标记并落盘顺序
                adapter.notifyChanged()
                BrowserTabs.persist()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.rvTabs)
    }

    /** 标签长按菜单：复制 / 关闭 */
    private fun showTabMenu(index: Int, anchor: View) {
        if (index !in BrowserTabs.tabs.indices) return
        currentMenu?.dismiss()
        val popup = PopupMenu(
            android.view.ContextThemeWrapper(this, R.style.AppTheme_PopupOverlay_Compact),
            anchor
        )
        popup.menu.add(0, 0, 0, "复制标签页")
        popup.menu.add(0, 1, 0, "关闭标签页")
        popup.setOnMenuItemClickListener { item ->
            currentMenu = null
            when (item.itemId) {
                0 -> {
                    BrowserTabs.onDuplicate?.invoke(index)
                    finish()
                }

                1 -> {
                    BrowserTabs.onClose?.invoke(index)
                    refresh()
                }
            }
            true
        }
        popup.setOnDismissListener { currentMenu = null }
        currentMenu = popup
        popup.show()
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
            holder.binding.root.setOnLongClickListener {
                // 长按由 ItemTouchHelper 处理（弹菜单/拖动），不触发点击切换
                true
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
