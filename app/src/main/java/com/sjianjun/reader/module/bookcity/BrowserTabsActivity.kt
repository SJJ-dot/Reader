package com.sjianjun.reader.module.bookcity

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.PopupWindow
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
import com.sjianjun.reader.module.browser.BrowserTab
import com.sjianjun.reader.module.browser.TabRepository

/** 标签页管理界面（Edge 风格网格） */
class BrowserTabsActivity : BaseActivity() {

    private val binding by lazy { ActivityBrowserTabsBinding.inflate(layoutInflater) }
    private val adapter = TabsAdapter()
    private var keyword = ""
    /** 当前长按弹出的菜单（拖动开始时关闭） */
    private var currentMenu: PopupWindow? = null
    /** 本次长按是否发生了拖动排序 */
    private var dragMoved = false

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
            TabRepository.add(BrowserTab(url = ""), makeActive = true)
            TabRepository.persist()
            finish()
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val list = TabRepository.list.filter {
            keyword.isBlank() ||
                    it.title.contains(keyword, true) ||
                    it.url.contains(keyword, true)
        }
        // 按打开顺序排序（标签列表本身即打开顺序）
        adapter.submit(list)
        // 自动定位到当前显示的标签
        val activeTab = TabRepository.getActive()
        val activePos = if (activeTab != null) list.indexOf(activeTab) else -1
        if (activePos >= 0) {
            binding.rvTabs.post { binding.rvTabs.scrollToPosition(activePos) }
        }
    }

    /** 长按立即弹菜单；开始拖动时隐藏菜单并进入排序（搜索过滤时禁用） */
    private fun attachDragSort() {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
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
                dragMoved = true
                dismissTabMenu()
                adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                // 手指位移超过系统拖动阈值时隐藏菜单
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG &&
                    (kotlin.math.abs(dX) > touchSlop || kotlin.math.abs(dY) > touchSlop)
                ) {
                    dismissTabMenu()
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // 长按：立即弹出菜单（不等松手）
                    dragMoved = false
                    val vh = viewHolder ?: return
                    val pos = vh.bindingAdapterPosition
                    if (pos >= 0) {
                        showTabMenu(pos, vh.itemView)
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (dragMoved) {
                    // 只有真正拖动排序后才刷新 active 标记并落库；
                    // 长按未拖动时避免 notifyDataSetChanged 干扰菜单定位
                    adapter.notifyChanged()
                    TabRepository.persist()
                    dragMoved = false
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.rvTabs)
    }

    /** 标签长按菜单：复制 / 关闭（模态，点击外部/返回键自动关闭） */
    private fun showTabMenu(index: Int, anchor: View) {
        val list = adapter.currentList
        if (index !in list.indices) return
        dismissTabMenu()
        val content = LayoutInflater.from(this).inflate(R.layout.popup_tab_menu, null)
        val popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isFocusable = true
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        content.findViewById<View>(R.id.menu_duplicate).setOnClickListener {
            dismissTabMenu()
            val src = adapter.currentList[index]
            TabRepository.add(
                BrowserTab(title = src.title.ifBlank { "新标签页" }, url = src.url, transient = src.transient),
                makeActive = true
            )
            TabRepository.persist()
            refresh()
        }
        content.findViewById<View>(R.id.menu_close).setOnClickListener {
            dismissTabMenu()
            val tab = adapter.currentList[index]
            TabRepository.deleteThumb(tab)
            TabRepository.deleteState(tab)
            if (TabRepository.size <= 1) {
                TabRepository.remove(tab.id)
                TabRepository.add(BrowserTab(url = ""), makeActive = true)
            } else {
                TabRepository.remove(tab.id)
            }
            TabRepository.persist()
            refresh()
        }
        popup.setOnDismissListener { currentMenu = null }
        currentMenu = popup
        // 用 showAtLocation 手动定位，避免 showAsDropDown 注册 layout 监听导致松手后自动重定位
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        popup.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, loc[0], loc[1] + anchor.height + 4)
    }

    private fun dismissTabMenu() {
        currentMenu?.dismiss()
        currentMenu = null
    }

    private inner class TabsAdapter : RecyclerView.Adapter<TabsAdapter.Holder>() {
        private val data = mutableListOf<BrowserTab>()

        val currentList: List<BrowserTab> get() = data

        fun submit(list: List<BrowserTab>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        /** 拖动调整位置：同步重排底层标签列表与展示列表 */
        fun move(from: Int, to: Int) {
            if (from == to || from !in data.indices || to !in data.indices) return
            val activeTab = TabRepository.getActive()
            val moved = data.removeAt(from)
            data.add(to, moved)
            val ordered = data.map { it.id }
            TabRepository.reorder(ordered)
            TabRepository.setActive(activeTab?.id)
            notifyItemMoved(from, to)
        }

        fun notifyChanged() = notifyDataSetChanged()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(ItemBrowserTabBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val tab = data[position]
            val active = tab.id == TabRepository.getActive()?.id
            holder.binding.ivThumb.setImageBitmap(
                tab.thumbPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            )
            holder.binding.tvTitle.text = tab.title.ifBlank { "无标题" }
            holder.binding.tvUrl.text = tab.url
            holder.binding.root.strokeWidth = if (active) 3 else 0
            holder.binding.root.strokeColor = ContextCompat.getColor(
                holder.itemView.context, R.color.colorPrimary
            )
            holder.binding.root.setOnClickListener {
                TabRepository.setActive(tab.id)
                TabRepository.persist()
                finish()
            }
            holder.binding.root.setOnLongClickListener {
                // 长按由 ItemTouchHelper 处理（弹菜单/拖动），不触发点击切换
                true
            }
            holder.binding.btnClose.setOnClickListener {
                TabRepository.deleteThumb(tab)
                TabRepository.deleteState(tab)
                if (TabRepository.size <= 1) {
                    TabRepository.remove(tab.id)
                    TabRepository.add(BrowserTab(url = ""), makeActive = true)
                } else {
                    TabRepository.remove(tab.id)
                }
                TabRepository.persist()
                refresh()
            }
        }

        inner class Holder(val binding: ItemBrowserTabBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
