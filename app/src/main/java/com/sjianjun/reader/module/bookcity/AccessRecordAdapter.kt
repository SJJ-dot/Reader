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
            val value: String,
            val mode: String,
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

    /** 从完整 URL 解析成 "host:port"（携带端口，纯 IP 退化为 "IP:port"），便于按端口区分与匹配 */
    private fun hostPortOf(url: String): String {
        val u = url.toHttpUrlOrNull() ?: return url
        return "${u.host}:${u.port}"
    }

    /** 注册域（不含端口）；纯 IP 退化为 IP 本身。修复 topPrivateDomain() 对 IP 返回 null 导致空白的问题 */
    private fun registrableOf(host: String): String {
        val h = parseRuleHost(host).first
        val u = "https://$h".toHttpUrlOrNull() ?: return h
        return u.topPrivateDomain() ?: h
    }

    /** 当前类型筛选：null=全部；否则只显示该类别（图片/脚本/样式/字体/视频/音频/其他）的请求 */
    private var filterType: String? = null

    /** 设置类型筛选；category 与筛选栏 chip 的 tag 一致（null=全部） */
    fun setTypeFilter(category: String?) {
        filterType = category
        rebuild()
    }

    /** 当前列表实际出现的资源类型集合（不含"全部"），用于动态隐藏无数据的筛选按钮 */
    fun activeCategories(): Set<String> {
        val set = mutableSetOf<String>()
        for (page in source) for (r in page.requests) set.add(resourceCategory(r.url))
        return set
    }

    /** 由 URL 扩展名归类到展示用的类型大类（与筛选栏 chip 的 tag 一致） */
    private fun resourceCategory(url: String): String {
        return when (resourceType(url)) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> "图片"
            "js" -> "脚本"
            "css" -> "样式"
            "woff", "woff2", "ttf", "eot", "otf" -> "字体"
            "mp4", "webm", "ogv", "mov", "m4v" -> "视频"
            "mp3", "wav", "ogg", "m4a", "aac" -> "音频"
            else -> "其他"
        }
    }

    private fun rebuild() {
        val sorted = source.sortedByDescending { it.time }
        val flat = mutableListOf<Row>()
        for (page in sorted) {
            // 类型筛选：仅保留选中类别的请求；筛选生效且无匹配时整页隐藏
            val pageReqs = if (filterType == null) page.requests
            else page.requests.filter { resourceCategory(it.url) == filterType }
            val hpGroups = pageReqs.groupBy { hostPortOf(it.url) }
                .toList()
                .sortedBy { (_, reqs) -> reqs.minOf { it.time } }
            if (filterType != null && hpGroups.isEmpty()) continue
            flat.add(Row.Page(page))
            if (page.pageUrl in expanded) {
                for ((hp, reqs) in hpGroups) {
                    val hostOnly = parseRuleHost(hp).first
                    val reg = registrableOf(hostOnly)
                    val isTop = reg == hostOnly
                    val key = "${page.pageUrl}\t$hp"
                    // 顶级域名与子域名同缩进（level=1），拦截范围模式由 mode 区分
                    flat.add(Row.Node(1, page.pageUrl, hp, if (isTop) BLOCK_MODE_DOMAIN else BLOCK_MODE_SUBDOMAIN, reqs.size, reqs.sumOf { it.count }, key))
                    if (key in expanded) {
                        for (r in reqs) flat.add(Row.Url(page.pageUrl, r, ""))
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

    /** 由 URL 提取资源类型（文件扩展名，如 jpg/js/css/php），无扩展名返回"资源" */
    private fun resourceType(url: String): String {
        val path = url.lowercase().substringBefore('?').substringBefore('#')
        val lastSeg = path.substringAfterLast('/', "")
        val ext = Regex("""\.([a-z0-9]{1,5})($|[^a-z0-9])""").find(lastSeg)
            ?.groupValues?.get(1).orEmpty()
        return ext.ifBlank { "资源" }
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
            // 加载中显示转圈动画，加载完成切换为完成图标
            if (page.loading) {
                b.pbLoading.visibility = View.VISIBLE
                b.ivDone.visibility = View.GONE
            } else {
                b.pbLoading.visibility = View.GONE
                b.ivDone.visibility = View.VISIBLE
            }
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
            // 去掉“顶级域名/子域名”标题：标题位置改显示 子项数量·命中次数（主题色）
            b.tvLabel.text = "${row.count} 项 · 命中 ${row.hits} 次"
            b.tvLabel.setTextColor(ContextCompat.getColor(b.root.context, R.color.colorPrimary))
            b.tvValue.text = row.value
            b.tvCount.visibility = View.GONE
            b.ivChevron.rotation = if (row.key in expanded) 90f else 0f
            // 整卡缩进：缩进量加到卡片 marginStart，卡片边框随之右移
            indentCard(b.root, row.level)
            b.root.setOnClickListener {
                if (row.key in expanded) expanded.remove(row.key) else expanded.add(row.key)
                rebuild()
            }
            // 规则状态标记：白名单优先（与实际放行逻辑一致），子域名继承顶级域名规则。
            // 这样点顶级域名“+黑名单”后，真正承载规则的域名行会显示红色“已拦截”。
            val ownMode = row.mode
            val topOfValue = registrableOf(parseRuleHost(row.value).first)
            val isTop = ownMode == BLOCK_MODE_DOMAIN
            val whitelisted = if (isTop) {
                AdBlock.hasWhiteRule(row.value, ownMode)
            } else {
                AdBlock.hasWhiteRule(row.value, ownMode)
                        || AdBlock.hasWhiteRule(topOfValue, BLOCK_MODE_DOMAIN)
            }
            // 整站/子域 快捷加若无端口，按“默认端口”拆成 80、443 两条带端口规则，
            // 以满足“每条规则都必须带端口”（HTTP=80、HTTPS=443）。
            val rawHost = row.value
            val blackHosts = if (parseRuleHost(rawHost).second == null) {
                listOf("$rawHost:80", "$rawHost:443")
            } else {
                listOf(rawHost)
            }
            val parentHost = parseRuleHost(topOfValue).first
            val hasBlack = blackHosts.any { AdBlock.hasBlackRule(it, ownMode) }
                    || (!isTop && listOf("$parentHost:80", "$parentHost:443")
                .any { AdBlock.hasBlackRule(it, BLOCK_MODE_DOMAIN) })
            val blacklisted = hasBlack
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
            // 按钮：已有规则时显示“-移除”（含 80/443 两条），否则显示“+加入”
            if (hasBlack) {
                b.btnBlack.text = "-黑名单"
                b.btnBlack.setOnClickListener {
                    blackHosts.forEach { AdBlock.removeBlackHost(HostStr(it, listOf()).apply { mode = ownMode }) }
                    AdBlock.unmarkBlockedInAccess(rawHost, ownMode)
                }
            } else {
                b.btnBlack.text = "+黑名单"
                b.btnBlack.setOnClickListener {
                    blackHosts.forEach { AdBlock.addBlackHost(it, ownMode) }
                }
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
            indentCard(b.root, 2)

            // 资源类型 + 图片缩略图（便于判断是否为广告）
            val type = resourceType(req.url)
            b.tvType.text = type
            if (type in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")) {
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
    // 纯 IP 站点 topPrivateDomain() 返回 null，退化为 host（即 IP 本身），避免“顶级域名”选项为空
    val top = httpUrl?.topPrivateDomain() ?: httpUrl?.host.orEmpty()
    val host = httpUrl?.host.orEmpty()
    // 自动采用传入链接的端口：链接用 80 就记 :80、443 就记 :443、显式 :8080 就记 :8080，
    // 无需用户手填，且能精确区分端口（如屏蔽 example.com:80 而放行 :443）。
    // 精确URL 选项直接用原 URL（含端口或不含，按字符串精确匹配）。
    val port = httpUrl?.port
    val topVal = if (port != null) "$top:$port" else top
    val subVal = if (port != null) "$host:$port" else host
    val options = listOf(
        Triple("顶级域名（含所有子域名）", topVal, BLOCK_MODE_DOMAIN),
        Triple("子域名", subVal, BLOCK_MODE_SUBDOMAIN),
        Triple("精确URL", url, BLOCK_MODE_URL)
    )
    val view = DialogAdBlockScopeBinding.inflate(LayoutInflater.from(context))
    view.tvDomainVal.text = topVal
    view.tvSubVal.text = subVal
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
