package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.BaseActivity
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ActivityBrowserRecordsBinding
import com.sjianjun.reader.databinding.ItemBrowserRecordBinding
import com.sjianjun.reader.databinding.ItemBrowserRecordHeaderBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.utils.glide
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 收藏夹 / 历史记录列表页 */
class BrowserRecordsActivity : BaseActivity() {

    companion object {
        const val MODE = "mode"
        const val BOOKMARKS = "bookmarks"
        const val HISTORY = "history"
        const val CURRENT_TITLE = "current_title"
        const val CURRENT_URL = "current_url"
    }

    private data class Row(
        val title: String,
        val url: String,
        val time: Long,
        val removable: Boolean = false,
    )

    private sealed class Item {
        data class Header(val day: String, val expanded: Boolean) : Item()
        data class Record(val row: Row) : Item()
    }

    private val binding by lazy { ActivityBrowserRecordsBinding.inflate(layoutInflater) }
    private val adapter = RecordsAdapter()
    private var bookmarksMode = true
    private val collapsedDays = mutableSetOf<String>()
    private var bookmarksJob: Job? = null
    /** 当前长按弹出的菜单（拖动开始时关闭） */
    private var currentMenu: PopupWindow? = null
    /** 最近一次按下的屏幕坐标（用于让长按菜单在按压位置弹出） */
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        bookmarksMode = intent.getStringExtra(MODE) != HISTORY
        binding.tvTitle.text = if (bookmarksMode) "收藏夹" else "历史记录"
        binding.btnAdd.visibility = if (bookmarksMode) View.VISIBLE else View.GONE
        binding.rvRecords.layoutManager = LinearLayoutManager(this)
        binding.rvRecords.adapter = adapter
        attachBookmarkDragSort()
        binding.btnAdd.setOnClickListener {
            val url = intent.getStringExtra(CURRENT_URL).orEmpty()
            if (url.isBlank()) {
                toast("当前没有页面")
                return@setOnClickListener
            }
            BookmarkStore.add(intent.getStringExtra(CURRENT_TITLE).orEmpty(), url)
            toast("已收藏")
            reload()
        }
        binding.btnClear.setOnClickListener { confirmClear() }
        reload()
    }

    private fun reload() {
        if (bookmarksMode) reloadBookmarks() else reloadHistory()
    }

    private fun reloadBookmarks() {
        val saved = BookmarkStore.list().map { Row(it.title, it.url, it.time, true) }
        adapter.submit(saved.map { Item.Record(it) })
        lifecycleScope.launch {
            val sites = BookCityViewModel().getAllBookSourceSite().distinct()
            val extra = sites.filter { s -> saved.none { it.url == s.url } }
                .map { Row(it.title ?: it.url, it.url, 0, false) }
            adapter.submit((saved + extra).map { Item.Record(it) })
        }
    }

    private fun reloadHistory() {
        val groups = History.list
            .sortedByDescending { it.time }
            .groupBy { dateKey(it.time) }
        val items = mutableListOf<Item>()
        groups.entries.sortedByDescending { it.key }.forEach { (day, records) ->
            val expanded = !collapsedDays.contains(day)
            items.add(Item.Header(dayLabel(day), expanded))
            if (expanded) {
                records.forEach { item ->
                    items.add(
                        Item.Record(
                            Row(item.title.ifBlank { item.url }, item.url, item.time, true)
                        )
                    )
                }
            }
        }
        adapter.submit(items)
    }

    private fun dateKey(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))

    private fun dayLabel(key: String): String {
        val now = System.currentTimeMillis()
        return when (key) {
            dateKey(now) -> "今天"
            dateKey(now - 24 * 60 * 60 * 1000) -> "昨天"
            else -> key
        }
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清空")
            .setMessage(if (bookmarksMode) "确定清空收藏夹？" else "确定清空历史记录？")
            .setPositiveButton("清空") { _, _ ->
                if (bookmarksMode) BookmarkStore.clear() else History.list = emptyList()
                reload()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 编辑收藏项的名称与地址 */
    private fun showEditBookmarkDialog(row: Row) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = android.widget.EditText(this).apply {
            hint = "名称"
            setText(row.title)
            setSelection(text.length)
        }
        val urlInput = android.widget.EditText(this).apply {
            hint = "地址"
            setText(row.url)
            setSelection(row.url.length)
        }
        container.addView(nameInput)
        container.addView(urlInput)
        MaterialAlertDialogBuilder(this)
            .setTitle("编辑收藏")
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newTitle = nameInput.text.toString().trim()
                val newUrl = urlInput.text.toString().trim()
                if (newUrl.isBlank()) {
                    toast("地址不能为空")
                    return@setPositiveButton
                }
                BookmarkStore.update(row.url, newTitle, newUrl)
                toast("已保存")
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun faviconOf(url: String): String = runCatching {
        val uri = java.net.URI(url)
        "${uri.scheme}://${uri.host}/favicon.ico"
    }.getOrDefault(url)

    /** 收藏夹模式：长按可拖动排序（仅用户收藏），并弹出非模态菜单 */
    private fun attachBookmarkDragSort() {
        if (!bookmarksMode) return
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled(): Boolean = bookmarksMode

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                // 拖动开始：隐藏菜单
                dismissRecordMenu()
                if (!adapter.move(from, to)) return false
                BookmarkStore.move(from, to)
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
                    dismissRecordMenu()
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    val vh = viewHolder ?: return
                    val pos = vh.bindingAdapterPosition
                    val row = adapter.recordAt(pos) ?: return
                    showRecordMenu(row, vh.itemView)
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.rvRecords)
    }

    /**
     * 长按菜单（非模态，避免拦截拖动）。
     * @param useTouchPosition true = 在手指按压位置弹出；false = 紧贴条目下方（拖动场景）
     */
    private fun showRecordMenu(row: Row, anchor: View, useTouchPosition: Boolean = false) {
        dismissRecordMenu()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val textColor = ContextCompat.getColor(this, R.color.dn_text_color_black)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_popup_menu)
            setPadding(0, dp(6), 0, dp(6))
        }
        fun addItem(label: String, onClick: () -> Unit) {
            container.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(textColor)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setOnClickListener {
                    dismissRecordMenu()
                    onClick()
                }
            })
        }
        if (bookmarksMode && row.removable) {
            addItem("编辑") { showEditBookmarkDialog(row) }
        }
        addItem("在新标签页打开") {
            EventBus.post(EventKey.OPEN_BROWSER_URL_NEW_TAB, row.url)
            finish()
        }
        if (!bookmarksMode) {
            addItem("添加到收藏") {
                BookmarkStore.add(row.title.ifBlank { row.url }, row.url)
                toast("已收藏")
            }
        }
        addItem("设为主页") {
            if (row.url.isNotBlank()) {
                globalConfig.bookCityUrl = row.url
                toast("已设为主页")
            }
        }
        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isFocusable = true
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        popup.setOnDismissListener { currentMenu = null }
        currentMenu = popup
        // 用 showAtLocation 手动定位，避免 showAsDropDown 注册 layout 监听导致松手后自动重定位
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        // 先量出菜单尺寸，再以按压点为中心并限制在屏幕内
        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val menuW = container.measuredWidth
        val menuH = container.measuredHeight
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val margin = dp(8)
        val rawX = if (useTouchPosition) lastTouchX.toInt() - menuW / 2 else loc[0]
        val rawY = if (useTouchPosition) lastTouchY.toInt() else loc[1] + anchor.height + 4
        val x = rawX.coerceIn(margin, (screenW - menuW - margin).coerceAtLeast(margin))
        val y = rawY.coerceIn(margin, (screenH - menuH - margin).coerceAtLeast(margin))
        popup.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, x, y)
    }

    private fun dismissRecordMenu() {
        currentMenu?.dismiss()
        currentMenu = null
    }

    private inner class RecordsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val data = mutableListOf<Item>()

        fun submit(list: List<Item>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        fun recordAt(position: Int): Row? = (data.getOrNull(position) as? Item.Record)?.row

        /** 拖动调整位置：只允许用户收藏（removable）之间排序 */
        fun move(from: Int, to: Int): Boolean {
            if (from == to) return false
            val fromItem = data.getOrNull(from) as? Item.Record ?: return false
            val toItem = data.getOrNull(to) as? Item.Record ?: return false
            if (!fromItem.row.removable || !toItem.row.removable) return false
            val item = data.removeAt(from)
            data.add(to, item)
            notifyItemMoved(from, to)
            return true
        }

        override fun getItemViewType(position: Int): Int =
            if (data[position] is Item.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                HeaderHolder(ItemBrowserRecordHeaderBinding.inflate(inflater, parent, false))
            } else {
                RecordHolder(ItemBrowserRecordBinding.inflate(inflater, parent, false))
            }
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = data[position]) {
                is Item.Header -> (holder as HeaderHolder).bind(item)
                is Item.Record -> (holder as RecordHolder).bind(item.row)
            }
        }

        inner class HeaderHolder(val binding: ItemBrowserRecordHeaderBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Item.Header) {
                binding.tvDay.text = item.day
                binding.tvArrow.text = if (item.expanded) "▾" else "▸"
                binding.root.setOnClickListener {
                    val key = (data[adapterPosition] as? Item.Header)?.day ?: return@setOnClickListener
                    // 用原始日期 key 记录折叠状态
                    val rawKey = dayKeyOf(item.day)
                    if (!collapsedDays.add(rawKey)) collapsedDays.remove(rawKey)
                    reloadHistory()
                }
            }
        }

        inner class RecordHolder(val binding: ItemBrowserRecordBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(row: Row) {
                binding.ivIcon.setImageResource(R.drawable.ic_browser_globe)
                runCatching { binding.ivIcon.glide(faviconOf(row.url)) }
                binding.tvTitle.text = row.title.ifBlank { row.url }
                binding.tvUrl.text = row.url
                binding.root.setOnClickListener {
                    // 复用栈内已打开的浏览器，避免新建 Activity 与全局标签共享 WebView
                    EventBus.post(EventKey.OPEN_BROWSER_URL, row.url)
                    finish()
                }
                // 记录按压坐标，长按菜单据此在手指附近弹出
                binding.root.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                    false
                }
                if (!bookmarksMode) {
                    // 历史模式：长按弹菜单（收藏夹模式由 ItemTouchHelper 处理）
                    binding.root.setOnLongClickListener {
                        showRecordMenu(row, binding.root, useTouchPosition = true)
                        true
                    }
                }
                binding.btnDelete.visibility = if (row.removable) View.VISIBLE else View.GONE
                binding.btnDelete.setOnClickListener {
                    if (!row.removable) return@setOnClickListener
                    if (bookmarksMode) {
                        BookmarkStore.remove(row.url)
                    } else {
                        // 历史不再去重，按「地址 + 时间」精确删除这一条
                        History.list = History.list.filterNot { it.url == row.url && it.time == row.time }
                    }
                    reload()
                }
            }
        }
    }

    /** "今天"/"昨天" 只是展示文案，折叠状态要用真实日期 key */
    private fun dayKeyOf(label: String): String {
        val now = System.currentTimeMillis()
        return when (label) {
            "今天" -> dateKey(now)
            "昨天" -> dateKey(now - 24 * 60 * 60 * 1000)
            else -> label
        }
    }

}
