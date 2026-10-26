package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.DialogAdBlockScopeBinding
import com.sjianjun.reader.databinding.ItemAdBlockDomainBinding
import com.sjianjun.reader.databinding.ItemAdBlockPageBinding
import com.sjianjun.reader.databinding.ItemAdBlockUrlBinding
import com.sjianjun.reader.utils.glide
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 访问记录适配器：按网页分组（仿收藏夹 item），网页内再按
 * 顶级域名 → 子域名 → URL 三级分组，每层都可折叠、逐级缩进、子项均为卡片样式。
 * 注意：MaterialCardView 的内容缩进必须用 setContentPadding，setPadding 对卡片无效。
 * - 网页行：图标 + 标题 + url（默认折叠）；
 * - 顶级域名 / 子域名行：折叠箭头 + 中文标签 + 域名值 + 子项数量与命中次数 + 黑/白名单按钮；
 * - URL 行：单行中间缩略号 + 命中次数 + 被拦截标记（红色描边/文字）+ 操作按钮。
 *   · 正常请求：显示“+黑名单”；
 *   · 被拦截请求：显示“恢复”（移除对应黑名单规则，便于误拦截恢复）。
 */
class AccessRecordAdapter(
    private val onAddBlackDomain: (domain: String, mode: String) -> Unit,
    private val onAddWhiteDomain: (domain: String, mode: String) -> Unit,
    private val onAddBlackChoice: (url: String, domain: String) -> Unit,
    private val onRestore: (url: String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_PAGE = 0
        private const val TYPE_NODE = 1
        private const val TYPE_URL = 2
    }

    private var source: List<AccessPage> = emptyList()
    private val expanded = mutableSetOf<String>()

    private sealed class Row {
        data class Page(val page: AccessPage) : Row()
        data class Node(
            val level: Int,
            val pageUrl: String,
            val label: String,
            val value: String,
            val count: Int,
            val hits: Int,
            val key: String
        ) : Row()

        data class Url(val pageUrl: String, val req: AccessRequest, val key: String) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(list: List<AccessPage>) {
        source = list
        rebuild()
    }

    private fun topDomain(host: String): String =
        runCatching { "https://$host".toHttpUrlOrNull()?.topPrivateDomain() }
            .getOrDefault(host).orEmpty()

    private fun rebuild() {
        val sorted = source.sortedByDescending { it.time }
        val flat = mutableListOf<Row>()
        for (page in sorted) {
            flat.add(Row.Page(page))
            if (page.pageUrl in expanded) {
                val topGroups = page.requests.groupBy { topDomain(it.domain) }
                for ((top, topReqs) in topGroups) {
                    val topKey = "${page.pageUrl}\t$top"
                    flat.add(Row.Node(1, page.pageUrl, "顶级域名", top, topReqs.size, topReqs.sumOf { it.count }, topKey))
                    if (topKey in expanded) {
                        val subGroups = topReqs.groupBy { it.domain }
                        for ((sub, subReqs) in subGroups) {
                            val subKey = "${page.pageUrl}\t$top\t$sub"
                            flat.add(Row.Node(2, page.pageUrl, "子域名", sub, subReqs.size, subReqs.sumOf { it.count }, subKey))
                            if (subKey in expanded) {
                                for (r in subReqs) flat.add(Row.Url(page.pageUrl, r, ""))
                            }
                        }
                    }
                }
            }
        }
        rows = flat
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is Row.Page -> TYPE_PAGE
        is Row.Node -> TYPE_NODE
        is Row.Url -> TYPE_URL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_PAGE -> PageVH(ItemAdBlockPageBinding.inflate(inflater, parent, false))
            TYPE_NODE -> NodeVH(ItemAdBlockDomainBinding.inflate(inflater, parent, false))
            else -> UrlVH(ItemAdBlockUrlBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Page -> (holder as PageVH).bind(row)
            is Row.Node -> (holder as NodeVH).bind(row)
            is Row.Url -> (holder as UrlVH).bind(row)
        }
    }

    private fun indentPx(level: Int, context: Context): Int =
        ((8 + level * 16) * context.resources.displayMetrics.density).toInt()

    /** 由 URL 推断资源类型（用于访问记录里区分图片/脚本/样式等，便于判断广告） */
    private fun resourceType(url: String): String {
        val path = url.lowercase().substringBefore('?').substringBefore('#')
        return when {
            Regex(""".*\.(png|jpe?g|gif|webp|bmp|svg)(\?|#|$)""").matches(path) -> "图片"
            path.endsWith("/favicon.ico") -> "图标"
            Regex(""".*\.js(\?|#|$)""").matches(path) -> "脚本"
            Regex(""".*\.css(\?|#|$)""").matches(path) -> "样式"
            Regex(""".*\.(woff2?|ttf|eot|otf)(\?|#|$)""").matches(path) -> "字体"
            Regex(""".*\.(mp4|webm|ogv|mov|m4v)(\?|#|$)""").matches(path) -> "视频"
            Regex(""".*\.(mp3|wav|ogg|m4a|aac)(\?|#|$)""").matches(path) -> "音频"
            else -> "资源"
        }
    }

    /** 整卡缩进：把缩进量设置到卡片自身的 marginStart（卡片边框跟着缩进，而非仅内容） */
    private fun indentCard(view: android.view.View, level: Int) {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        lp.marginStart = indentPx(level, view.context)
        view.layoutParams = lp
    }

    private inner class PageVH(val b: ItemAdBlockPageBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row.Page) {
            val page = row.page
            b.tvTitle.text = page.title.ifBlank { page.pageUrl }
            b.tvUrl.text = page.pageUrl
            b.ivIcon.setImageResource(R.drawable.ic_browser_globe)
            runCatching { b.ivIcon.glide(page.favicon) }
            b.ivChevron.rotation = if (page.pageUrl in expanded) 90f else 0f
            b.root.setOnClickListener {
                val key = page.pageUrl
                if (key in expanded) expanded.remove(key) else expanded.add(key)
                rebuild()
            }
        }
    }

    private inner class NodeVH(val b: ItemAdBlockDomainBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row.Node) {
            b.tvLabel.text = row.label
            b.tvValue.text = row.value
            // 同时展示：子项数量 + 命中次数（该组下所有请求次数之和）
            b.tvCount.text = "${row.count} 项 · 命中 ${row.hits} 次"
            b.ivChevron.rotation = if (row.key in expanded) 90f else 0f
            // 整卡缩进：缩进量加到卡片 marginStart，卡片边框随之右移
            indentCard(b.root, row.level)
            b.root.setOnClickListener {
                if (row.key in expanded) expanded.remove(row.key) else expanded.add(row.key)
                rebuild()
            }
            // 规则状态标记：白名单优先（与实际放行逻辑一致），子域名继承顶级域名规则。
            // 这样点顶级域名“+黑名单”后，真正承载规则的域名行会显示红色“已拦截”。
            val ownMode = if (row.level == 1) BLOCK_MODE_DOMAIN else BLOCK_MODE_SUBDOMAIN
            val topOfValue = topDomain(row.value)
            val whitelisted = when (row.level) {
                1 -> AdBlock.hasWhiteRule(row.value, ownMode)
                else -> AdBlock.hasWhiteRule(row.value, ownMode)
                        || AdBlock.hasWhiteRule(topOfValue, BLOCK_MODE_DOMAIN)
            }
            val blacklisted = when (row.level) {
                1 -> AdBlock.hasBlackRule(row.value, ownMode)
                else -> AdBlock.hasBlackRule(row.value, ownMode)
                        || AdBlock.hasBlackRule(topOfValue, BLOCK_MODE_DOMAIN)
            }
            when {
                whitelisted -> {
                    b.tvState.visibility = View.VISIBLE
                    b.tvState.text = "已放行"
                    b.tvState.setTextColor(Color.parseColor("#2E7D32"))
                }
                blacklisted -> {
                    b.tvState.visibility = View.VISIBLE
                    b.tvState.text = "已拦截"
                    b.tvState.setTextColor(Color.parseColor("#E53935"))
                }
                else -> b.tvState.visibility = View.GONE
            }
            // 按钮：本行范围已有规则时显示“-移除”，否则显示“+加入”
            if (AdBlock.hasBlackRule(row.value, ownMode)) {
                b.btnBlack.text = "-黑名单"
                b.btnBlack.setOnClickListener {
                    AdBlock.removeBlackHost(HostStr(row.value, listOf()).apply { mode = ownMode })
                    AdBlock.unmarkBlockedInAccess(row.value, ownMode)
                }
            } else {
                b.btnBlack.text = "+黑名单"
                b.btnBlack.setOnClickListener { onAddBlackDomain(row.value, ownMode) }
            }
            if (AdBlock.hasWhiteRule(row.value, ownMode)) {
                b.btnWhite.text = "-白名单"
                b.btnWhite.setOnClickListener {
                    AdBlock.removeWhiteHost(HostStr(row.value, listOf()).apply { mode = ownMode })
                }
            } else {
                b.btnWhite.text = "+白名单"
                b.btnWhite.setOnClickListener { onAddWhiteDomain(row.value, ownMode) }
            }
        }
    }

    private inner class UrlVH(val b: ItemAdBlockUrlBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row.Url) {
            val req = row.req
            b.tvUrl.text = req.url
            indentCard(b.root, 3)

            // 资源类型 + 图片缩略图（便于判断是否为广告）
            val type = resourceType(req.url)
            b.tvType.text = type
            if (type == "图片") {
                b.ivThumb.visibility = View.VISIBLE
                runCatching { b.ivThumb.glide(req.url) }
            } else {
                b.ivThumb.visibility = View.GONE
            }

            // 命中次数 + 被拦截标记
            val blockedColor = Color.parseColor("#E53935")
            val grayColor = Color.parseColor("#888888")
            b.tvCount.text = if (req.blocked) "已拦截 ×${req.count}" else "×${req.count}"
            b.tvCount.setTextColor(if (req.blocked) blockedColor else grayColor)
            val density = b.root.context.resources.displayMetrics.density
            b.root.strokeColor = if (req.blocked) blockedColor else 0
            b.root.strokeWidth = if (req.blocked) (1 * density).toInt() else 0

            // 操作按钮：正常请求→加黑名单；被拦截→恢复（移除对应规则）
            if (req.blocked) {
                b.btnBlack.text = "恢复"
                b.btnBlack.setSolid(ContextCompat.getColor(b.root.context, R.color.colorAccent))
                b.btnBlack.setOnClickListener { onRestore(req.url) }
            } else {
                b.btnBlack.text = "+黑名单"
                b.btnBlack.setSolid(ContextCompat.getColor(b.root.context, R.color.colorPrimary))
                b.btnBlack.setOnClickListener { onAddBlackChoice(req.url, req.domain) }
            }
        }
    }
}

/**
 * 加入黑名单的“拦截范围”选择弹窗（自定义布局，比系统单选列表更好看）：
 * 顶级域名（含所有子域名）/ 子域名 / 精确URL，默认选顶级域名；
 * 各选项的值单行中间缩略号显示，确认后回调 value+mode。
 */
fun showBlockScopeDialog(
    context: Context,
    url: String,
    onConfirm: (value: String, mode: String) -> Unit
) {
    val httpUrl = url.toHttpUrlOrNull()
    val top = httpUrl?.topPrivateDomain().orEmpty()
    val host = httpUrl?.host.orEmpty()
    val options = listOf(
        Triple("顶级域名（含所有子域名）", top, BLOCK_MODE_DOMAIN),
        Triple("子域名", host, BLOCK_MODE_SUBDOMAIN),
        Triple("精确URL", url, BLOCK_MODE_URL)
    )
    val view = DialogAdBlockScopeBinding.inflate(LayoutInflater.from(context))
    view.tvDomainVal.text = top
    view.tvSubVal.text = host
    view.tvUrlVal.text = url

    var checked = 0
    val radios = listOf(view.rbDomain, view.rbSub, view.rbUrl)
    val opts = listOf(view.optDomain, view.optSub, view.optUrl)
    fun updateSelection(index: Int) {
        checked = index
        radios.forEachIndexed { i, rb -> rb.isChecked = i == index }
    }
    opts.forEachIndexed { i, v -> v.setOnClickListener { updateSelection(i) } }
    updateSelection(0)

    val dialog = MaterialAlertDialogBuilder(context).setView(view.root).create()
    view.btnCancel.setOnClickListener { dialog.dismiss() }
    view.btnOk.setOnClickListener {
        val (_, value, mode) = options[checked]
        if (value.isNotBlank()) onConfirm(value, mode)
        dialog.dismiss()
    }
    dialog.show()
}
