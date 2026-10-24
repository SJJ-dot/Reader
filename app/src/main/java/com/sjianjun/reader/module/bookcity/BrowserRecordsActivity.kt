package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
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
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
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

    private inner class RecordsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val data = mutableListOf<Item>()

        fun submit(list: List<Item>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
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
                binding.root.setOnLongClickListener {
                    val popup = PopupMenu(
                        android.view.ContextThemeWrapper(it.context, R.style.AppTheme_PopupOverlay_Compact),
                        binding.root
                    )
                    if (bookmarksMode && row.removable) {
                        popup.menu.add(0, 0, 0, "编辑")
                    }
                    popup.menu.add(0, 1, 0, "在新标签页打开")
                    if (!bookmarksMode) {
                        popup.menu.add(0, 2, 0, "添加到收藏")
                    }
                    popup.menu.add(0, 3, 0, "设为主页")
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            0 -> {
                                showEditBookmarkDialog(row)
                            }

                            1 -> {
                                EventBus.post(EventKey.OPEN_BROWSER_URL_NEW_TAB, row.url)
                                finish()
                            }

                            2 -> {
                                BookmarkStore.add(row.title.ifBlank { row.url }, row.url)
                                toast("已收藏")
                            }

                            3 -> {
                                if (row.url.isNotBlank()) {
                                    globalConfig.bookCityUrl = row.url
                                    toast("已设为主页")
                                }
                            }
                        }
                        true
                    }
                    popup.show()
                    true
                }
                binding.btnDelete.visibility = if (row.removable) View.VISIBLE else View.GONE
                binding.btnDelete.setOnClickListener {
                    if (!row.removable) return@setOnClickListener
                    if (bookmarksMode) {
                        BookmarkStore.remove(row.url)
                    } else {
                        History.list = History.list.filterNot { it.url == row.url }
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
