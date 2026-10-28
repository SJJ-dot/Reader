package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
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
import com.sjianjun.reader.databinding.ItemBrowserBookmarkFolderBinding
import com.sjianjun.reader.databinding.ItemBrowserRecordBinding
import com.sjianjun.reader.databinding.ItemBrowserRecordHeaderBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.event.EventBus
import com.sjianjun.reader.event.EventKey
import com.sjianjun.reader.utils.glide
import com.sjianjun.reader.utils.toast
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
        data class Folder(
            val folder: BookmarkStore.Folder,
            val depth: Int,
            val expanded: Boolean,
            val childCount: Int,
        ) : Item()
        data class BookmarkNode(
            val bookmarkId: String,
            val row: Row,
            val depth: Int,
            val folderId: String,
        ) : Item()
    }

    private val binding by lazy { ActivityBrowserRecordsBinding.inflate(layoutInflater) }
    private val adapter = RecordsAdapter()
    private var bookmarksMode = true
    private val collapsedDays = mutableSetOf<String>()
    private val expandedFolderIds = mutableSetOf<String>()
    private var sourceSiteRows: List<Row> = emptyList()
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var currentMenu: PopupWindow? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
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
        bookmarksMode = intent.getStringExtra(MODE) != HISTORY
        binding.tvTitle.text = if (bookmarksMode) "收藏夹" else "历史记录"
        binding.btnAdd.visibility = if (bookmarksMode) View.VISIBLE else View.GONE
        binding.btnNewFolder.visibility = if (bookmarksMode) View.VISIBLE else View.GONE
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
        binding.btnNewFolder.setOnClickListener { showNewFolderDialog(BookmarkStore.ROOT) }
        binding.btnClear.setOnClickListener { confirmClear() }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                keyword = s?.toString()?.trim().orEmpty()
                reload()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        reload()
    }

    private fun reload() {
        if (bookmarksMode) reloadBookmarks() else reloadHistory()
    }

    private fun reloadBookmarks() {
        rebuildBookmarkTree()
        lifecycleScope.launch {
            val sites = runCatching { BookCityViewModel().getAllBookSourceSite().distinct() }
                .getOrDefault(emptyList())
            sourceSiteRows = sites.map { Row(it.title ?: it.url, it.url, 0, false) }
            rebuildBookmarkTree()
        }
    }

    private fun rebuildBookmarkTree() {
        val items = mutableListOf<Item>()
        buildBookmarkTree(BookmarkStore.ROOT, 0, items)
        adapter.submit(items)
    }

    private fun buildBookmarkTree(folderId: String, depth: Int, out: MutableList<Item>) {
        val kw = keyword
        val bookmarks = BookmarkStore.bookmarksIn(folderId)
            .filter { kw.isBlank() || it.title.contains(kw, true) || it.url.contains(kw, true) }
        val sites = if (folderId == BookmarkStore.BOOK_SOURCE_FOLDER_ID)
            sourceSiteRows.filter { kw.isBlank() || it.title.contains(kw, true) || it.url.contains(kw, true) }
        else emptyList()
        val children = BookmarkStore.childrenFolders(folderId)
        // 同层：用户收藏在前，书源站点随后，子文件夹最后
        bookmarks.forEach { b ->
            out += Item.BookmarkNode(b.id, Row(b.title, b.url, b.time, true), depth, folderId)
        }
        sites.forEach { r ->
            out += Item.BookmarkNode("", r, depth, folderId)
        }
        children.forEach { f ->
            if (kw.isBlank()) {
                // 非搜索：保持原样（含子文件夹计数）
                val count = BookmarkStore.bookmarksIn(f.id).size +
                    (if (f.id == BookmarkStore.BOOK_SOURCE_FOLDER_ID) sourceSiteRows.size else 0) +
                    BookmarkStore.childrenFolders(f.id).size
                val expanded = f.id in expandedFolderIds
                out += Item.Folder(f, depth, expanded, count)
                if (expanded) buildBookmarkTree(f.id, depth + 1, out)
            } else {
                // 搜索：递归构建子内容，仅当存在匹配项时保留该文件夹并展开
                val childItems = mutableListOf<Item>()
                buildBookmarkTree(f.id, depth + 1, childItems)
                if (childItems.isNotEmpty()) {
                    out += Item.Folder(f, depth, true, childItems.size)
                    out += childItems
                }
            }
        }
    }

    private fun reloadHistory() {
        val kw = keyword
        val groups = History.list()
            .filter { kw.isBlank() || it.title.contains(kw, true) || it.url.contains(kw, true) }
            .sortedByDescending { it.time }
            .groupBy { dateKey(it.time) }
        val items = mutableListOf<Item>()
        groups.entries.sortedByDescending { it.key }.forEach { (day, records) ->
            val expanded = !collapsedDays.contains(day)
            items.add(Item.Header(dayLabel(day), expanded))
            if (expanded) {
                records.forEach { item ->
                    items.add(Item.BookmarkNode("", Row(item.title.ifBlank { item.url }, item.url, item.time, true), 0, ""))
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
                if (bookmarksMode) BookmarkStore.clear() else History.clear()
                reload()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 编辑收藏项的名称与地址 */
    private fun showEditBookmarkDialog(node: Item.BookmarkNode) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(this).apply {
            hint = "名称"
            setText(node.row.title)
            setSelection(text.length)
        }
        val urlInput = EditText(this).apply {
            hint = "地址"
            setText(node.row.url)
            setSelection(node.row.url.length)
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
                BookmarkStore.updateById(node.bookmarkId, newTitle, newUrl)
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
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                dismissRecordMenu()
                if (!adapter.move(from, to)) return false
                return true
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean,
            ) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
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
                    val node = adapter.nodeAt(vh.bindingAdapterPosition) ?: return
                    showRecordMenu(node, vh.itemView, useTouchPosition = true)
                }
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.rvRecords)
    }

    /**
     * 长按菜单（非模态，避免拦截拖动）。
     * @param useTouchPosition true = 在手指按压位置弹出；false = 紧贴条目下方（拖动场景）
     */
    private fun showRecordMenu(node: Item.BookmarkNode, anchor: View, useTouchPosition: Boolean = false) {
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
        if (bookmarksMode && node.row.removable) {
            addItem("编辑") { showEditBookmarkDialog(node) }
            addItem("移动到…") { showMoveToDialog(node) }
        }
        addItem("在新标签页打开") {
            EventBus.post(EventKey.OPEN_BROWSER_URL_NEW_TAB, node.row.url)
            finish()
        }
        if (!bookmarksMode) {
            addItem("添加到收藏") {
                BookmarkStore.add(node.row.title.ifBlank { node.row.url }, node.row.url)
                toast("已收藏")
            }
        }
        addItem("设为主页") {
            if (node.row.url.isNotBlank()) {
                globalConfig.bookCityUrl = node.row.url
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
        showPopupAt(popup, container, anchor, useTouchPosition)
    }

    private fun showFolderMenu(folder: BookmarkStore.Folder, anchor: View, useTouchPosition: Boolean = false) {
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
        addItem("重命名") { showRenameFolderDialog(folder) }
        addItem("新建子文件夹") { showNewFolderDialog(folder.id) }
        if (!folder.builtin) {
            addItem("删除") { showDeleteFolderConfirm(folder) }
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
        showPopupAt(popup, container, anchor, useTouchPosition)
    }

    /**
     * 统一长按菜单位置：X 跟手（以手指水平位置居中）；Y 贴条目边缘
     * （下方有空间就贴下边缘，否则贴下边缘会被挤出屏幕时改贴上边缘）。
     */
    private fun showPopupAt(popup: PopupWindow, container: View, anchor: View, useTouchPosition: Boolean) {
        val density = resources.displayMetrics.density
        val margin = (8 * density).toInt()
        val gap = (4 * density).toInt()
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val menuW = container.measuredWidth
        val menuH = container.measuredHeight
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val rawX = if (useTouchPosition) (lastTouchX - menuW / 2f).toInt() else loc[0]
        val rawY = if (loc[1] + anchor.height + gap + menuH <= screenH - margin) {
            loc[1] + anchor.height + gap
        } else {
            (loc[1] - menuH - gap).coerceAtLeast(margin)
        }
        val x = rawX.coerceIn(margin, (screenW - menuW - margin).coerceAtLeast(margin))
        val y = rawY.coerceIn(margin, (screenH - menuH - margin).coerceAtLeast(margin))
        popup.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, x, y)
    }

    private fun showNewFolderDialog(parentId: String) {
        val input = EditText(this).apply { hint = "文件夹名称" }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("新建文件夹")
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    toast("名称不能为空")
                    return@setPositiveButton
                }
                BookmarkStore.addFolder(name, parentId)
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameFolderDialog(folder: BookmarkStore.Folder) {
        val input = EditText(this).apply {
            setText(folder.name)
            setSelection(folder.name.length)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名文件夹")
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    toast("名称不能为空")
                    return@setPositiveButton
                }
                BookmarkStore.renameFolder(folder.id, name)
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteFolderConfirm(folder: BookmarkStore.Folder) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除文件夹「${folder.name}」")
            .setMessage("文件夹内的书签和子文件夹将一并删除")
            .setPositiveButton("删除") { _, _ ->
                if (BookmarkStore.removeFolder(folder.id)) reload() else toast("内置文件夹不可删除")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 逐级浏览文件夹选择移动目标：列表只显示当前层级的子文件夹，可“上一级”返回 */
    private fun showMoveToDialog(node: Item.BookmarkNode) {
        val map = BookmarkStore.folders().associateBy { it.id }
        var currentId = BookmarkStore.ROOT

        fun pathOf(id: String): String {
            val parts = mutableListOf<String>()
            var cur = map[id]
            var guard = 0
            while (cur != null && guard++ < 20) {
                parts.add(0, cur.name)
                cur = map[cur.parentId]
            }
            return if (parts.isEmpty()) "根目录" else parts.joinToString(" / ")
        }

        fun subFoldersOf(id: String) = map.values.filter { it.parentId == id }.sortedBy { it.name }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        val listView = ListView(this).apply { this.adapter = adapter }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("移动到…")
            .setView(listView)
            .setPositiveButton("移动到此处", null)
            .setNegativeButton("取消", null)
            .setNeutralButton("上一级", null)
            .create()

        fun refresh() {
            dialog.setTitle("移动到：${pathOf(currentId)}")
            adapter.clear()
            adapter.addAll(subFoldersOf(currentId).map { "📁 ${it.name}" })
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = currentId != BookmarkStore.ROOT
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val target = subFoldersOf(currentId).getOrNull(position) ?: return@setOnItemClickListener
            currentId = target.id
            refresh()
        }

        dialog.setOnShowListener {
            refresh()
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (currentId != BookmarkStore.ROOT) {
                    currentId = map[currentId]?.parentId ?: BookmarkStore.ROOT
                    refresh()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                BookmarkStore.moveBookmark(node.bookmarkId, currentId)
                toast("已移动到「${pathOf(currentId)}」")
                dialog.dismiss()
                reload()
            }
        }
        dialog.show()
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

        fun nodeAt(position: Int): Item.BookmarkNode? = (data.getOrNull(position) as? Item.BookmarkNode)

        fun recordAt(position: Int): Row? = nodeAt(position)?.row

        /** 拖动调整位置：仅允许同一文件夹内、用户收藏之间排序 */
        fun move(from: Int, to: Int): Boolean {
            if (from == to) return false
            val fromItem = data.getOrNull(from) as? Item.BookmarkNode ?: return false
            val toItem = data.getOrNull(to) as? Item.BookmarkNode ?: return false
            if (fromItem.folderId != toItem.folderId) return false
            if (!fromItem.row.removable || !toItem.row.removable) return false
            val item = data.removeAt(from)
            data.add(to, item)
            notifyItemMoved(from, to)
            val ids = data.filterIsInstance<Item.BookmarkNode>()
                .filter { it.folderId == fromItem.folderId && it.bookmarkId.isNotEmpty() }
                .map { it.bookmarkId }
            BookmarkStore.setOrder(ids)
            return true
        }

        override fun getItemViewType(position: Int): Int = when (data[position]) {
            is Item.Header -> 0
            is Item.BookmarkNode -> 1
            is Item.Folder -> 2
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> HeaderHolder(ItemBrowserRecordHeaderBinding.inflate(inflater, parent, false))
                1 -> RecordHolder(ItemBrowserRecordBinding.inflate(inflater, parent, false))
                else -> FolderHolder(ItemBrowserBookmarkFolderBinding.inflate(inflater, parent, false))
            }
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = data[position]) {
                is Item.Header -> (holder as HeaderHolder).bind(item)
                is Item.BookmarkNode -> (holder as RecordHolder).bind(item)
                is Item.Folder -> (holder as FolderHolder).bind(item)
            }
        }

        inner class HeaderHolder(val binding: ItemBrowserRecordHeaderBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Item.Header) {
                binding.tvDay.text = item.day
                binding.tvArrow.rotation = if (item.expanded) 90f else 0f
                binding.root.setOnClickListener {
                    val key = (data[adapterPosition] as? Item.Header)?.day ?: return@setOnClickListener
                    val rawKey = dayKeyOf(item.day)
                    if (!collapsedDays.add(rawKey)) collapsedDays.remove(rawKey)
                    reloadHistory()
                }
            }
        }

        inner class RecordHolder(val binding: ItemBrowserRecordBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(node: Item.BookmarkNode) {
                val density = resources.displayMetrics.density
                val base = (10 * density).toInt()
                // 层级缩进作用于整张卡片（含背景），而非仅内容
                binding.root.layoutParams?.let {
                    (it as ViewGroup.MarginLayoutParams).marginStart =
                        (node.depth * 16 * density).toInt()
                    binding.root.layoutParams = it
                }
                binding.content.setPadding(base, (8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt())
                val row = node.row
                binding.ivIcon.setImageResource(R.drawable.ic_browser_globe)
                runCatching { binding.ivIcon.glide(faviconOf(row.url)) }
                binding.tvTitle.text = row.title.ifBlank { row.url }
                binding.tvUrl.text = row.url
                binding.root.setOnClickListener {
                    EventBus.post(EventKey.OPEN_BROWSER_URL, row.url)
                    finish()
                }
                binding.root.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                    false
                }
                if (!bookmarksMode) {
                    binding.root.setOnLongClickListener {
                        showRecordMenu(node, binding.root, useTouchPosition = true)
                        true
                    }
                } else {
                    binding.root.setOnLongClickListener {
                        if (row.removable) itemTouchHelper.startDrag(this)
                        else showRecordMenu(node, binding.root, useTouchPosition = true)
                        true
                    }
                }
                binding.btnDelete.visibility = if (row.removable) View.VISIBLE else View.GONE
                binding.btnDelete.setOnClickListener {
                    if (!row.removable) return@setOnClickListener
                    if (bookmarksMode) {
                        BookmarkStore.removeById(node.bookmarkId)
                    } else {
                        History.remove(row.url, row.time)
                    }
                    reload()
                }
            }
        }

        inner class FolderHolder(val binding: ItemBrowserBookmarkFolderBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Item.Folder) {
                val density = resources.displayMetrics.density
                val base = (10 * density).toInt()
                // 层级缩进作用于整张卡片（含背景），而非仅内容
                binding.root.layoutParams?.let {
                    (it as ViewGroup.MarginLayoutParams).marginStart =
                        (item.depth * 16 * density).toInt()
                    binding.root.layoutParams = it
                }
                binding.content.setPadding(base, base, base, base)
                binding.tvArrow.rotation = if (item.expanded) 90f else 0f
                binding.tvName.text = item.folder.name
                binding.tvCount.text = item.childCount.toString()
                binding.tvCount.visibility = if (item.childCount > 0) View.VISIBLE else View.GONE
                binding.root.setOnClickListener {
                    val f = (data[adapterPosition] as? Item.Folder)?.folder ?: return@setOnClickListener
                    if (!expandedFolderIds.add(f.id)) expandedFolderIds.remove(f.id)
                    rebuildBookmarkTree()
                }
                binding.root.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                    false
                }
                binding.root.setOnLongClickListener {
                    showFolderMenu(item.folder, binding.root, useTouchPosition = true)
                    true
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
