package com.sjianjun.reader.module.bookcity

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.adapter.BaseAdapter
import com.sjianjun.reader.databinding.FragmentBookCityPageHistoryItemBinding
import com.sjianjun.reader.databinding.FragmentBookCityPageHostItemBinding
import com.sjianjun.reader.databinding.ItemAdBlockMonthBinding
import com.sjianjun.reader.utils.color
import com.sjianjun.reader.utils.setTextColorRes
import com.sjianjun.reader.view.click
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 把拦截范围 mode 转成中文标签 */
private fun modeLabel(mode: String): String = when (mode) {
    BLOCK_MODE_DOMAIN -> "顶级域名"
    BLOCK_MODE_SUBDOMAIN -> "子域名"
    BLOCK_MODE_URL -> "精确URL"
    else -> "顶级域名"
}

/** 规则创建时间字符串（yyyy-MM-dd HH:mm:ss）解析成毫秒，解析失败返回 0 */
private fun createdTimeMs(time: String): Long = runCatching {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).parse(time)?.time ?: 0L
}.getOrDefault(0L)

/** 排序/分组依据：最近命中时间优先，从未命中则用规则创建时间；两者都无则 0（归入「未知时间」） */
internal fun hostSortTime(e: HostStr): Long {
    if (e.lastHitTime > 0) return e.lastHitTime
    return createdTimeMs(e.time)
}

/** 按月分组结果：[key] 为 "yyyy-MM" 或 "unknown"，[label] 为用户可见的月份标题 */
internal data class HostMonthGroup(
    val key: String,
    val label: String,
    val entries: List<HostStr>
)

/**
 * 按最近命中时间倒序排序，并按「最近命中时间所在月份」分组（顶层纯函数，可 JVM 单测）。
 * 月份倒序：最近命中的月份排最前；从未命中的规则按其创建时间归月。
 */
internal fun groupHostsByMonth(source: List<HostStr>): List<HostMonthGroup> {
    val keyFmt = SimpleDateFormat("yyyy-MM", Locale.CHINA)
    val labelFmt = SimpleDateFormat("yyyy年MM月", Locale.CHINA)
    val sorted = source.sortedWith(
        compareByDescending<HostStr> { hostSortTime(it) }.thenBy { it.host }
    )
    val grouped = LinkedHashMap<String, MutableList<HostStr>>()
    for (e in sorted) {
        val t = hostSortTime(e)
        val key = if (t > 0) keyFmt.format(Date(t)) else "unknown"
        grouped.getOrPut(key) { mutableListOf() }.add(e)
    }
    return grouped.map { (key, entries) ->
        val label = if (key == "unknown") "未知时间" else
            runCatching { labelFmt.format(keyFmt.parse(key)!!) }.getOrDefault(key)
        HostMonthGroup(key, label, entries)
    }
}

/**
 * 黑名单 / 白名单列表：按最近命中时间倒序、按月分组，月份可折叠，
 * 月份标题上带「清空」按钮（确认后只删除该月归属的规则）。
 */
class HostListAdapter(
    private val kind: String,
    private val adBlock: AdBlock,
    private val onRemoveRules: (List<HostStr>) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_MONTH = 0
        private const val TYPE_RULE = 1
    }

    private sealed class Row {
        data class Month(val group: HostMonthGroup, val collapsed: Boolean) : Row()
        data class Rule(val host: HostStr) : Row()
    }

    private val listLabel = if (kind == "black") "黑名单" else "白名单"
    private val hitFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val collapsedMonths = mutableSetOf<String>()
    private var source: List<HostStr> = emptyList()
    private var rows: List<Row> = emptyList()

    /** 提交最新名单：折叠状态按月份 key 保留，命中时间变化后自动重排 */
    fun submit(list: List<HostStr>) {
        source = list.toList()
        val flat = mutableListOf<Row>()
        for (g in groupHostsByMonth(source)) {
            val collapsed = g.key in collapsedMonths
            flat.add(Row.Month(g, collapsed))
            if (!collapsed) g.entries.forEach { flat.add(Row.Rule(it)) }
        }
        rows = flat
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is Row.Month -> TYPE_MONTH
        is Row.Rule -> TYPE_RULE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_MONTH) {
            MonthVH(ItemAdBlockMonthBinding.inflate(inflater, parent, false))
        } else {
            RuleVH(FragmentBookCityPageHostItemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Month -> (holder as MonthVH).bind(row)
            is Row.Rule -> (holder as RuleVH).bind(row.host)
        }
    }

    private inner class MonthVH(val b: ItemAdBlockMonthBinding) : RecyclerView.ViewHolder(b.root) {
        @SuppressLint("SetTextI18n")
        fun bind(row: Row.Month) {
            val g = row.group
            b.tvMonth.text = "${g.label} · ${g.entries.size} 条"
            b.ivChevron.rotation = if (row.collapsed) 0f else 90f
            b.root.click {
                if (g.key in collapsedMonths) collapsedMonths.remove(g.key)
                else collapsedMonths.add(g.key)
                submit(source)
            }
            b.btnClearMonth.click {
                MaterialAlertDialogBuilder(b.root.context)
                    .setTitle("清空$listLabel")
                    .setMessage("确定清空 ${g.label} 的 ${g.entries.size} 条${listLabel}规则？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清空") { _, _ -> onRemoveRules(g.entries) }
                    .show()
            }
        }
    }

    private inner class RuleVH(val b: FragmentBookCityPageHostItemBinding) : RecyclerView.ViewHolder(b.root) {
        @SuppressLint("SetTextI18n")
        fun bind(host: HostStr) {
            val ctx = b.root.context
            if (host.isPage) {
                b.root.setCardBackgroundColor(R.color.colorPrimary.color(ctx))
                b.tvHost.setTextColorRes(R.color.mdr_white)
                b.tvType.setTextColorRes(R.color.mdr_white)
                b.tvTime.setTextColorRes(R.color.mdr_white)
                b.btnMarkBlack.setTextColorRes(R.color.colorPrimary)
                b.btnMarkBlack.setSolid(R.color.mdr_white.color(ctx))
            } else {
                b.root.setCardBackgroundColor(R.color.dn_bookcity_host_item_background.color(ctx))
                b.tvHost.setTextColorRes(R.color.dn_text_color_black)
                b.tvType.setTextColorRes(R.color.colorPrimary)
                b.tvTime.setTextColorRes(R.color.dn_text_color_black)
                b.btnMarkBlack.setTextColorRes(R.color.mdr_white)
                b.btnMarkBlack.setSolid(R.color.colorPrimary.color(ctx))
            }
            b.tvType.text = "${modeLabel(host.mode)} · 命中 ${adBlock.getHitCount(host.host, host.mode)} 次"
            b.tvHost.text = host.host
            b.tvTime.text = if (host.lastHitTime > 0) {
                "最近命中 ${hitFmt.format(Date(host.lastHitTime))}"
            } else {
                "从未命中 · ${host.time.ifBlank { "未知时间" }}"
            }
            b.btnMarkBlack.text = "-移除"
            b.btnMarkBlack.click { onRemoveRules(listOf(host)) }
        }
    }
}

class HistoryListAdapter : BaseAdapter<HistoryItem>(R.layout.fragment_book_city_page_history_item) {
    var onClick: ((url: String) -> Unit)? = null
    var onDeleteHistory: ((item: HistoryItem?) -> Unit)? = null
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val binding = FragmentBookCityPageHistoryItemBinding.bind(holder.itemView)
        val history = data[position]
        binding.tvTitle.text = history.title
        binding.tvTime.text = sdf.format(history.time)
        binding.root.click {
            onClick?.invoke(history.url)
        }
        binding.root.setOnLongClickListener {
            MaterialAlertDialogBuilder(it.context).apply {
                setTitle("删除记录")
                setMessage("是否删除 ${history.title} ?")
                setNegativeButton("取消", null)
                setPositiveButton("删除") { _, _ ->
                    onDeleteHistory?.invoke(history)
                }
                setNeutralButton("清空") { _, _ ->
                    MaterialAlertDialogBuilder(it.context).apply {
                        setTitle("清空全部记录")
                        setMessage("是否清空全部历史记录 ?")
                        setNegativeButton("取消", null)
                        setPositiveButton("清空") { _, _ ->
                            onDeleteHistory?.invoke(null)
                        }.show()
                    }
                }
            }.show()
            return@setOnLongClickListener true
        }
    }
}
