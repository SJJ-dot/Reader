package com.sjianjun.reader.module.bookcity

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.coorchice.library.SuperTextView
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.ItemAdBlockDomainBinding
import com.sjianjun.reader.databinding.ItemAdBlockPageBinding
import com.sjianjun.reader.databinding.ItemAdBlockUrlBinding
import com.sjianjun.reader.utils.glide

/** 黑白名单按钮互斥时的可见性：返回 (黑名单按钮可见, 白名单按钮可见) */
internal fun adBlockRuleButtonsVisible(hasBlack: Boolean, hasWhite: Boolean): Pair<Boolean, Boolean> =
    (hasBlack || !hasWhite) to (hasWhite || !hasBlack)

/**
 * 访问记录：网页 → 顶级域名 → 子域名 → URL 三级分组，每层可折叠、逐级缩进。
 * - 顶级域名：值为注册域（如 example.com，不含协议/端口），规则用 BLOCK_MODE_DOMAIN，
 *   匹配该域下所有子域名与所有协议、端口；
 * - 子域名：值为 "scheme://host:port"（如 https://ads.example.com:443），规则用 BLOCK_MODE_SUBDOMAIN，
 *   协议与端口都参与匹配；显式端口相同但协议不同仍是两条独立规则；
 * - URL：值为完整 URL，规则用 BLOCK_MODE_URL，字符串精确匹配。
 *
 * 三级各有 +黑名单/-黑名单、+白名单/-白名单 按钮，点击即设置/移除本级规则，不弹范围选择框。
 * 注意：MaterialCardView 的内容缩进必须用 setContentPadding，setPadding 对卡片无效；
 * 这里改用的是整卡 marginStart，让卡片边框一起缩进。
 */
class AccessRecordAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_PAGE = 0
        private const val TYPE_NODE = 1
        private const val TYPE_URL = 2

        /**
         * 展开状态放在静态域：广告拦截页按返回键会被销毁重建（hasSavedState=false，
         * onSaveInstanceState 不会走），若只存在 adapter 实例里，重进后全部折叠、列表看起来像空。
         */
        private val expandedState = mutableSetOf<String>()
    }

    private sealed class Row {
        data class Page(val page: AccessPage) : Row()

        /**
         * [level] 1=顶级域名 2=子域名；[value] 为新增规则值，[ruleValues] 为状态查询/移除用的规则值集合；
         * [blockedCount] 为本级下实际被拦截的请求数（含父级规则造成的拦截），用于显示继承的「已拦截」。
         */
        data class Node(
            val level: Int,
            val value: String,
            val mode: String,
            val count: Int,
            val hits: Int,
            val key: String,
            val ruleValues: List<String>,
            val blockedCount: Int
        ) : Row()

        data class Url(val req: AccessRequest) : Row()
    }

    private var source: List<AccessPage> = emptyList()
    private val expanded: MutableSet<String> get() = expandedState
    private var rows: List<Row> = emptyList()

    /** 当前类型筛选：null=全部；否则只显示该类别（图片/脚本/样式/字体/视频/音频/其他）的请求 */
    private var filterType: String? = null

    fun submit(list: List<AccessPage>) {
        adBlockLog { "adapter.submit pages=${list.size} reqs=${list.sumOf { it.requests.size }} filterType=$filterType expanded=${expanded.size}" }
        source = list
        rebuild()
    }

    /** 设置类型筛选；category 与筛选栏 chip 的 tag 一致（null=全部） */
    fun setTypeFilter(category: String?) {
        adBlockLog { "adapter.setTypeFilter $filterType → $category" }
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
            // 一级：顶级域名（不含协议、端口）
            val domainGroups = pageReqs.groupBy { adBlockTopOf(it.url) }
                .toList()
                .sortedBy { (_, reqs) -> reqs.minOf { it.time } }
            if (filterType != null && domainGroups.isEmpty()) continue
            flat.add(Row.Page(page))
            if (page.pageUrl !in expanded) continue
            for ((top, domainReqs) in domainGroups) {
                val domainKey = "${page.pageUrl}\tD\t$top"
                flat.add(
                    Row.Node(
                        level = 1,
                        value = top,
                        mode = BLOCK_MODE_DOMAIN,
                        count = domainReqs.size,
                        hits = domainReqs.sumOf { it.count },
                        key = domainKey,
                        // 兼容历史「整站加黑」拆成 :80/:443 两条的旧规则，状态与移除一并覆盖
                        ruleValues = listOf(top, "$top:80", "$top:443"),
                        blockedCount = domainReqs.count { it.blocked }
                    )
                )
                if (domainKey !in expanded) continue
                // 二级：子域名 scheme://host:port（同时区分协议、端口）
                val subGroups = domainReqs.groupBy { adBlockSubdomainOf(it.url) }
                    .toList()
                    .sortedBy { (_, reqs) -> reqs.minOf { it.time } }
                for ((subValue, subReqs) in subGroups) {
                    val subKey = "${page.pageUrl}\tS\t$top\t$subValue"
                    flat.add(
                        Row.Node(
                            level = 2,
                            value = subValue,
                            mode = BLOCK_MODE_SUBDOMAIN,
                            count = subReqs.size,
                            hits = subReqs.sumOf { it.count },
                            key = subKey,
                            // 兼容历史不带协议的 "host:port" 规则（状态与移除一并覆盖）
                            ruleValues = listOf(subValue, subValue.substringAfter("://")).distinct(),
                            blockedCount = subReqs.count { it.blocked }
                        )
                    )
                    if (subKey !in expanded) continue
                    // 三级：精确 URL
                    for (r in subReqs) flat.add(Row.Url(r))
                }
            }
        }
        rows = flat
        adBlockLog { "adapter.rebuild rows=${rows.size} pages=${source.size} filterType=$filterType expanded=$expanded" }
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
        ((4 + level * 12) * context.resources.displayMetrics.density).toInt()

    /** 由 URL 提取资源类型（文件扩展名，如 jpg/js/css/php），无扩展名返回"资源" */
    private fun resourceType(url: String): String {
        val path = url.lowercase().substringBefore('?').substringBefore('#')
        val lastSeg = path.substringAfterLast('/', "")
        val ext = Regex("""\.([a-z0-9]{1,5})($|[^a-z0-9])""").find(lastSeg)
            ?.groupValues?.get(1).orEmpty()
        return ext.ifBlank { "资源" }
    }

    /** 整卡缩进：把缩进量设置到卡片自身的 marginStart（卡片边框跟着缩进，而非仅内容） */
    private fun indentCard(view: View, level: Int) {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        lp.marginStart = indentPx(level, view.context)
        view.layoutParams = lp
    }

    /** 三级共用的状态标签：只反映本级规则，不因父级/子级规则而上溯或下推 */
    private fun showState(tv: TextView, hasWhite: Boolean, hasBlack: Boolean) {
        when {
            hasWhite -> {
                tv.visibility = View.VISIBLE
                tv.text = "已放行"
                tv.setTextColor(Color.parseColor("#2E7D32"))
            }
            hasBlack -> {
                tv.visibility = View.VISIBLE
                tv.text = "已拦截"
                tv.setTextColor(Color.parseColor("#E53935"))
            }
            else -> tv.visibility = View.GONE
        }
    }

    /**
     * 三级共用的黑/白名单按钮，二者互斥：本级已有黑名单时不再提供「+白名单」，
     * 必须先点「-黑名单」移除；已有白名单时同理反过来。
     * [addValue] 是新增时使用的规则值；[queryValues] 是状态查询与移除时覆盖的规则值
     * （顶级域名额外兼容历史带 80/443 端口的旧规则）。
     */
    private fun bindRuleButtons(
        blackBtn: SuperTextView,
        whiteBtn: SuperTextView,
        addValue: String,
        queryValues: List<String>,
        mode: String
    ) {
        val hasBlack = queryValues.any { AdBlock.hasBlackRule(it, mode) }
        val hasWhite = queryValues.any { AdBlock.hasWhiteRule(it, mode) }
        // 对方已有规则时隐藏自己的「+」入口；两者都有（历史脏数据）时保留两个「-」以便清理
        val (showBlack, showWhite) = adBlockRuleButtonsVisible(hasBlack, hasWhite)
        blackBtn.visibility = if (showBlack) View.VISIBLE else View.GONE
        whiteBtn.visibility = if (showWhite) View.VISIBLE else View.GONE
        blackBtn.setSolid(ContextCompat.getColor(blackBtn.context, R.color.colorPrimary))
        whiteBtn.setSolid(Color.parseColor("#03A9F4"))

        blackBtn.text = if (hasBlack) "-黑名单" else "+黑名单"
        blackBtn.setOnClickListener {
            if (hasBlack) {
                queryValues.forEach { AdBlock.removeBlackHost(HostStr(it).apply { this.mode = mode }) }
            } else {
                AdBlock.addBlackHost(addValue, mode)
            }
        }
        whiteBtn.text = if (hasWhite) "-白名单" else "+白名单"
        whiteBtn.setOnClickListener {
            if (hasWhite) {
                queryValues.forEach { AdBlock.removeWhiteHost(HostStr(it).apply { this.mode = mode }) }
            } else {
                AdBlock.addWhiteHost(addValue, mode)
            }
        }
    }

    private inner class PageVH(val b: ItemAdBlockPageBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row.Page) {
            val page = row.page
            b.tvTitle.text = page.title.ifBlank { page.pageUrl }
            b.tvUrl.text = page.pageUrl
            b.ivIcon.setImageResource(R.drawable.ic_browser_globe)
            runCatching { b.ivIcon.glide(page.favicon) }
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
            val levelLabel = if (row.level == 1) "顶级域名" else "子域名"
            b.tvLabel.text = "$levelLabel · ${row.count} 项 · ${row.hits} 次"
            b.tvLabel.setTextColor(ContextCompat.getColor(b.root.context, R.color.colorPrimary))
            b.tvValue.text = row.value
            b.tvCount.visibility = View.GONE
            b.ivChevron.rotation = if (row.key in expanded) 90f else 0f
            indentCard(b.root, row.level)
            b.root.setOnClickListener {
                if (row.key in expanded) expanded.remove(row.key) else expanded.add(row.key)
                rebuild()
            }
            // 状态标签反映实际生效结果：除本级规则外，父级（顶级域名）规则拦下的请求
            // 也要让子域名节点显示「已拦截」；按钮状态仍严格按本级规则，避免出现点不动的「-」。
            val ownWhite = row.ruleValues.any { AdBlock.hasWhiteRule(it, row.mode) }
            val ownBlack = row.ruleValues.any { AdBlock.hasBlackRule(it, row.mode) }
            showState(b.tvState, ownWhite, ownBlack || row.blockedCount > 0)
            bindRuleButtons(b.btnBlack, b.btnWhite, row.value, row.ruleValues, row.mode)
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
            if (type in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")) {
                b.ivThumb.visibility = View.VISIBLE
                runCatching { b.ivThumb.glide(req.url) }
            } else {
                b.ivThumb.visibility = View.GONE
            }

            // 实际是否被某条黑名单规则拦截（由 AdBlock.recomputeBlockedMarks 精确重算）
            val blockedColor = Color.parseColor("#E53935")
            val grayColor = Color.parseColor("#888888")
            b.tvCount.text = if (req.blocked) "已拦截 ×${req.count}" else "×${req.count}"
            b.tvCount.setTextColor(if (req.blocked) blockedColor else grayColor)
            val density = b.root.context.resources.displayMetrics.density
            b.root.strokeColor = if (req.blocked) blockedColor else 0
            b.root.strokeWidth = if (req.blocked) (1 * density).toInt() else 0

            // 本级按钮：直接增删「精确URL」规则，不再弹范围选择框
            bindRuleButtons(b.btnBlack, b.btnWhite, req.url, listOf(req.url), BLOCK_MODE_URL)
        }
    }
}
