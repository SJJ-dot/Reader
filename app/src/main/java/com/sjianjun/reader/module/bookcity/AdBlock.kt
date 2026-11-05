package com.sjianjun.reader.module.bookcity

import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.sjianjun.reader.bean.AdBlockHostEntity
import com.sjianjun.reader.repository.DbFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import com.sjianjun.reader.BuildConfig
import sjj.alog.Log

/** 排查用操作日志：统一前缀 ▶AdBlock，release 不输出（DEBUG 为编译期常量，lambda 不会执行） */
internal inline fun adBlockLog(block: () -> String) {
    if (BuildConfig.DEBUG) Log.i("▶AdBlock ${block()}")
}

/** 拦截范围：顶级域名（含所有子域名） */
const val BLOCK_MODE_DOMAIN = "domain"

/** 拦截范围：精确子域名（不含其它子域名） */
const val BLOCK_MODE_SUBDOMAIN = "subdomain"

/** 拦截范围：精确 URL */
const val BLOCK_MODE_URL = "url"

class HostStr @JvmOverloads constructor(
    var host: String = "",
    var type: List<String> = emptyList()
) {
    var isPage = false
    var time: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(System.currentTimeMillis())
    /** 拦截/放行范围：顶级域名 / 子域名 / 精确URL（见 BLOCK_MODE_*） */
    var mode: String = BLOCK_MODE_DOMAIN

    /** 最近一次命中时间（毫秒，0 = 从未命中）；落库，用于列表按最近命中时间倒序并按月分组 */
    @Volatile
    var lastHitTime: Long = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HostStr
        return host == other.host && mode == other.mode
    }

    override fun hashCode(): Int = 31 * host.hashCode() + mode.hashCode()
}

/** 一次网络请求的记录：完整 URL + 所属域名 + 命中次数 + 是否被拦截 */
data class AccessRequest(
    val url: String,
    val domain: String,
    var count: Int = 1,
    var blocked: Boolean = false,
    var time: Long = 0
)

/** 一个网页（主框架）的访问记录：含该网页请求到的所有 URL */
data class AccessPage(
    val pageUrl: String,
    var time: Long,
    var title: String = "",
    var favicon: String = "",
    val requests: MutableList<AccessRequest> = mutableListOf(),
    var loading: Boolean = false
)

// ===================== 匹配（纯函数，可单测；支持 顶级域名 / 子域名 / 精确URL / 纯IP / 端口） =====================

/** 解析规则里的 host（可能带 ":端口"），返回 (纯host, 端口?)。端口仅对 IPv4/域名解析；IPv6 整体作 host。规则不含端口→null（匹配该 host 所有端口，向后兼容“屏蔽整站”）。 */
internal fun parseRuleHost(ruleHostRaw: String): Pair<String, Int?> {
    if (ruleHostRaw.startsWith("[") || ruleHostRaw.count { it == ':' } > 1) {
        return ruleHostRaw to null
    }
    val idx = ruleHostRaw.lastIndexOf(':')
    if (idx > 0) {
        val port = ruleHostRaw.substring(idx + 1).toIntOrNull()
        if (port != null && port in 1..65535) {
            return ruleHostRaw.substring(0, idx) to port
        }
    }
    return ruleHostRaw to null
}

/** 子域名规则解析结果：[scheme] 为 null 表示旧格式规则（不带协议，匹配该 host:port 的所有协议） */
internal data class SubdomainRule(val scheme: String?, val host: String, val port: Int?)

/** 解析子域名规则值：新格式 "scheme://host:port"（同时区分协议与端口），兼容旧格式 "host:port" / "host" */
internal fun parseSubdomainRule(raw: String): SubdomainRule {
    val scheme = if ("://" in raw) raw.substringBefore("://").lowercase() else null
    val hostPort = if (scheme != null) raw.substringAfter("://") else raw
    val (host, port) = parseRuleHost(hostPort)
    return SubdomainRule(scheme, host, port)
}

/** 判断单条规则是否匹配该 URL：纯 IP 退化为 host；端口 URL 侧用 httpUrl.port（默认80/443），规则带端口则精确匹配；子域名规则带协议时协议也须一致。 */
internal fun adBlockRuleMatches(e: HostStr, httpUrl: HttpUrl, top: String?, url: String): Boolean {
    val (ruleHost, rulePort) = parseRuleHost(e.host)
    val urlHost = httpUrl.host
    val urlPort = httpUrl.port
    return when (e.mode) {
        BLOCK_MODE_URL -> e.host == url
        BLOCK_MODE_SUBDOMAIN -> {
            val sub = parseSubdomainRule(e.host)
            sub.host == urlHost
                    && (sub.port == null || sub.port == urlPort)
                    && (sub.scheme == null || sub.scheme == httpUrl.scheme.lowercase())
        }
        else -> {
            val base = top ?: urlHost
            ruleHost == base && (rulePort == null || rulePort == urlPort)
        }
    }
}

/** 从完整 URL 取 "host:port"（未写端口时按协议取 80/443），用于顶级域名解析与旧规则兼容 */
internal fun adBlockHostPortOf(url: String): String {
    val u = url.toHttpUrlOrNull() ?: return url
    return "${u.host}:${u.port}"
}

/**
 * 三级规则值：从完整 URL 取 "scheme://host:port"，用于子域名级。
 * 显式端口相同但协议不同也算两条不同规则，例如
 * http://ads.example.com:12345、http://ads.example.com:12346、https://ads.example.com:12345 互不相同。
 */
internal fun adBlockSubdomainOf(url: String): String {
    val u = url.toHttpUrlOrNull() ?: return url
    return "${u.scheme}://${u.host}:${u.port}"
}

/** 三级规则值：从完整 URL 取顶级域名（不含协议、端口），用于顶级域名级（不区分协议、端口）；纯 IP 退化为 IP 本身 */
internal fun adBlockTopOf(url: String): String {
    val host = parseRuleHost(adBlockHostPortOf(url)).first
    val u = "https://$host".toHttpUrlOrNull() ?: return host
    return u.topPrivateDomain() ?: host
}

/** 一次规则判定的结果：[rule] 为实际生效的规则，[kind] 为 "black"/"white" */
data class AdBlockHit(val rule: HostStr, val kind: String) {
    val allowed: Boolean get() = kind == "white"
}

/**
 * 规则优先级：**精确URL > 子域名 > 顶级域名**。
 * 逐级检查该级是否有匹配当前请求的黑/白名单规则，命中即返回，不再看更低优先级级别；
 * 同级黑白名单互斥，故同级先白后黑不影响结果。
 * 顶级域名级同时容纳历史未知/空 mode 的规则（与 adBlockRuleMatches 的 else 分支一致）。
 */
internal fun adBlockResolve(
    u: HttpUrl,
    url: String,
    top: String?,
    blackRules: Collection<HostStr>,
    whiteRules: Collection<HostStr>
): AdBlockHit? {
    // 1) 精确 URL
    whiteRules.firstOrNull { it.mode == BLOCK_MODE_URL && adBlockRuleMatches(it, u, top, url) }
        ?.let { return AdBlockHit(it, "white") }
    blackRules.firstOrNull { it.mode == BLOCK_MODE_URL && adBlockRuleMatches(it, u, top, url) }
        ?.let { return AdBlockHit(it, "black") }
    // 2) 子域名（scheme://host:port）
    whiteRules.firstOrNull { it.mode == BLOCK_MODE_SUBDOMAIN && adBlockRuleMatches(it, u, top, url) }
        ?.let { return AdBlockHit(it, "white") }
    blackRules.firstOrNull { it.mode == BLOCK_MODE_SUBDOMAIN && adBlockRuleMatches(it, u, top, url) }
        ?.let { return AdBlockHit(it, "black") }
    // 3) 顶级域名
    whiteRules.firstOrNull {
        it.mode != BLOCK_MODE_URL && it.mode != BLOCK_MODE_SUBDOMAIN && adBlockRuleMatches(it, u, top, url)
    }?.let { return AdBlockHit(it, "white") }
    blackRules.firstOrNull {
        it.mode != BLOCK_MODE_URL && it.mode != BLOCK_MODE_SUBDOMAIN && adBlockRuleMatches(it, u, top, url)
    }?.let { return AdBlockHit(it, "black") }
    return null
}

/**
 * 按当前规则判断某 URL 是否应被拦截（走与运行时一致的层级优先级）。
 * [top] 由调用方传入（运行时取 httpUrl.topPrivateDomain()，纯 JVM 单测可硬编码以避开公共后缀库）。
 */
internal fun adBlockIsBlocked(
    url: String,
    blackRules: List<HostStr>,
    whiteRules: List<HostStr>,
    top: String?
): Boolean {
    val u = url.toHttpUrlOrNull() ?: return false
    return adBlockResolve(u, url, top, blackRules, whiteRules)?.allowed == false
}

object AdBlock {
    /** 命中时间写库节流：同一规则 30 秒内最多写一次 */
    private const val HIT_PERSIST_INTERVAL_MS = 30_000L

    /** 命中后列表重排通知节流 */
    private const val HIT_NOTIFY_INTERVAL_MS = 1_000L

    private val dao get() = DbFactory.db.browserDataDao()
    private val gson = Gson()

    val blacklist = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())
    val whitelist = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())

    /**
     * 访问记录（拦截记录）：按网页分组，**仅保存在内存，不落库**。
     * 列表本身始终是唯一可变实例，所有改动就地完成后 postValue 通知观察者。
     */
    val accessPages = MutableLiveData<MutableList<AccessPage>>(mutableListOf())

    /** 当前正在加载的网页（用于把请求归到对应网页分组下） */
    @Volatile
    private var currentPage: AccessPage? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 访问记录读写锁：shouldInterceptRequest 可能在多个线程并发回调，必须串行化 */
    private val recordLock = Any()

    /** UI 刷新节流时间戳 */
    @Volatile
    private var lastFlush = 0L

    /** APP 启动后在子线程预加载：把 DB 中的黑白名单读入内存缓存 */
    fun preload() {
        scope.launch { loadFromDb() }
    }

    private fun loadFromDb() {
        val black = dao.getAdBlock("black").map { it.toHostStr() }
        val white = dao.getAdBlock("white").map { it.toHostStr() }
        blacklist.postValue(CopyOnWriteArrayList(black))
        whitelist.postValue(CopyOnWriteArrayList(white))
    }

    /**
     * 落库某类黑白名单：以内存列表为准，只删除「库中存在但内存已无」的规则（按 host+kind 主键），
     * 再 upsert 现有规则；列表为空时（用户点了清空）整类清表。
     */
    private fun persist(kind: String, list: List<HostStr>) {
        // 同步拍快照：内存列表是就地修改的，异步落库期间若被后续增删改动会导致写入结果错乱
        val snapshot = list.toList()
        scope.launch {
            if (snapshot.isEmpty()) {
                dao.clearAdBlock(kind)
                return@launch
            }
            val oldHosts = dao.getAdBlock(kind).map { it.host }.toSet()
            val newHosts = snapshot.map { it.host }.toSet()
            val toDelete = (oldHosts - newHosts).toList()
            if (toDelete.isNotEmpty()) dao.deleteAdBlockByHosts(kind, toDelete)
            val toInsert = snapshot.map {
                AdBlockHostEntity(it.host, kind, gson.toJson(it.type), it.isPage, it.time, it.mode, it.lastHitTime)
            }
            dao.insertAdBlock(toInsert)
        }
    }

    // ===================== 匹配（支持 顶级域名 / 子域名 / 精确URL / 纯IP / 端口） =====================
    // 匹配核心逻辑见文件顶层 internal 函数 adBlockRuleMatches / parseRuleHost（可单测，不依赖本单例）。

    /**
     * 按「精确URL > 子域名 > 顶级域名」判定一次请求，返回实际生效的规则（无规则命中返回 null），
     * 并累计该规则的命中次数与最近命中时间。一次请求只判定一次，避免重复计数。
     */
    fun resolve(url: String): AdBlockHit? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val hit = adBlockResolve(
            httpUrl, url, httpUrl.topPrivateDomain(),
            blacklist.value ?: emptyList(),
            whitelist.value ?: emptyList()
        ) ?: return null
        recordHit(hit.kind, hit.rule)
        return hit
    }

    // ===================== 命中统计（次数内存累计 / 最近命中时间落库） =====================

    private val hitCounts = ConcurrentHashMap<String, Int>()

    /** 每条规则最近一次落库的命中时间，用于节流写库 */
    private val persistedHitAt = ConcurrentHashMap<String, Long>()

    @Volatile
    private var lastHitNotify = 0L

    private fun hitKey(host: String, mode: String) = "$host#$mode"

    /**
     * 累计一次命中：更新内存中的命中次数与规则的最近命中时间，
     * 并节流地写库、通知列表重排（网络请求是高频回调，不能每次都落库/刷新）。
     */
    private fun recordHit(kind: String, entry: HostStr) {
        val now = System.currentTimeMillis()
        entry.lastHitTime = now
        val key = hitKey(entry.host, entry.mode)
        hitCounts[key] = (hitCounts[key] ?: 0) + 1

        val persistKey = "$kind#$key"
        if (now - (persistedHitAt[persistKey] ?: 0L) >= HIT_PERSIST_INTERVAL_MS) {
            persistedHitAt[persistKey] = now
            val host = entry.host
            scope.launch { runCatching { dao.updateAdBlockHitTime(kind, host, now) } }
        }
        if (now - lastHitNotify >= HIT_NOTIFY_INTERVAL_MS) {
            lastHitNotify = now
            if (kind == "black") blacklist.postValue(blacklist.value)
            else whitelist.postValue(whitelist.value)
        }
    }

    /** 读取某条规则的命中次数（供黑白名单列表展示） */
    fun getHitCount(host: String, mode: String): Int = hitCounts[hitKey(host, mode)] ?: 0

    /** 是否存在指定范围的黑名单规则 */
    fun hasBlackRule(host: String, mode: String): Boolean =
        blacklist.value?.any { it.host == host && it.mode == mode } ?: false

    /** 是否存在指定范围的白名单规则 */
    fun hasWhiteRule(host: String, mode: String): Boolean =
        whitelist.value?.any { it.host == host && it.mode == mode } ?: false

    /**
     * 兼容裸域名与完整 URL 的顶级域名解析。
     * 访问记录里的 r.domain 是裸 host（如 "soduzw.org"），直接 toHttpUrlOrNull() 会因缺少
     * scheme 得到 null，导致按范围匹配时 null==null 误命中全部请求，这里统一补 scheme 再解析。
     */
    /** 域名/IP 的统一匹配键：域名取顶级域名，纯 IP 或无法解析时退化为 host 本身 */
    private fun domainKey(hostOrUrl: String): String? {
        val u = hostOrUrl.toHttpUrlOrNull() ?: "https://$hostOrUrl".toHttpUrlOrNull()
        val top = u?.topPrivateDomain()
        return top ?: u?.host
    }

    // ===================== 访问记录（内存，不落库） =====================
    // shouldInterceptRequest 会在 WebView 的多个线程并发回调，
    // 因此所有对访问记录的读写都用 recordLock 串行化，避免 ConcurrentModificationException。

    /** 标记一个新的网页访问（主框架加载），作为后续请求的归属分组 */
    fun markPage(url: String?) {
        if (url.isNullOrBlank()) return
        adBlockLog { "markPage 入口 url=$url pagesBefore=${accessPages.value?.size}" }
        synchronized(recordLock) {
            val list = accessPages.value ?: mutableListOf()
            var page = list.firstOrNull { it.pageUrl == url }
            if (page == null) {
                adBlockLog { "markPage 新建分组 url=$url" }
                page = AccessPage(url, System.currentTimeMillis(), favicon = faviconOf(url))
                list.add(0, page)
            } else {
                adBlockLog { "markPage 复用分组 url=$url reqs=${page.requests.size}" }
                list.remove(page)
                page.time = System.currentTimeMillis()
                list.add(0, page)
            }
            page.loading = true
            currentPage = page
            // 仅当前正在加载的页面显示加载中：切换页面（前进/后退/新链接）时清掉旧页面状态，
            // 避免旧页面 loading 永远停在 true（如加载中后退再前进导致 onPageFinished 漏触发）
            list.forEach { it.loading = it === page }
        }
        adBlockLog { "markPage 完成 url=$url pages=${accessPages.value?.size} currentPage=${currentPage?.pageUrl}" }
        accessPages.postValue(accessPages.value)
    }

    /** 主框架加载完成后补充网页标题（图标在 markPage 时已按 favicon.ico 推导） */
    fun updatePageMeta(url: String?, title: String?) {
        if (url.isNullOrBlank()) return
        synchronized(recordLock) {
            accessPages.value?.firstOrNull { it.pageUrl == url }?.let { page ->
                if (!title.isNullOrBlank()) page.title = title
            }
        }
        accessPages.postValue(accessPages.value)
    }

    /** 主框架加载完成：清除加载状态，供 UI 把转圈动画切换为完成图标 */
    fun finishPage(url: String?) {
        if (url.isNullOrBlank()) return
        synchronized(recordLock) {
            accessPages.value?.firstOrNull { it.pageUrl == url }?.loading = false
        }
        accessPages.postValue(accessPages.value)
    }

    /** 进度到达 100% 时的兜底：清除当前页加载状态，避免 onPageFinished 漏触发导致一直转圈 */
    fun finishCurrentPage() {
        synchronized(recordLock) {
            currentPage?.loading = false
        }
        accessPages.postValue(accessPages.value)
    }

    /**
     * 页面再次成为“可见页”时刷新最后显示时间（用于访问记录按最后显示时间排序）。
     * 后退/前进/历史/SPA 等场景 onPageStarted 不触发时，由 doUpdateVisitedHistory 调用，
     * 确保例如 A→B→C 后退到 B 后，访问记录从上到下排序为 B、C、A。
     */
    fun touchPage(url: String?) {
        if (url.isNullOrBlank()) return
        synchronized(recordLock) {
            val page = accessPages.value?.firstOrNull { it.pageUrl == url } ?: return
            page.time = System.currentTimeMillis()
            currentPage = page
        }
        accessPages.postValue(accessPages.value)
    }

    /** 由网页地址推导 favicon 地址（与收藏夹一致：host/favicon.ico） */
    private fun faviconOf(url: String): String = runCatching {
        val uri = java.net.URI(url)
        "${uri.scheme}://${uri.host}/favicon.ico"
    }.getOrDefault(url)

    /**
     * 记录一次网络请求，归入当前网页分组。
     * [blocked] 是本次请求是否被实际拦截（黑名单规则或跨域拦截都算）；**被拦截的请求同样计入记录**，
     * 以便查看拦截效果和做误拦恢复。白名单放行的请求由调用方提前返回，不调用本方法。
     */
    fun addUrl(url: String, blocked: Boolean) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val domain = httpUrl.host
        adBlockLog { "addUrl 入口 url=$url blocked=$blocked currentPage=${currentPage?.pageUrl} pages=${accessPages.value?.size}" }
        synchronized(recordLock) {
            val page = currentPage
            if (page == null) {
                adBlockLog { "addUrl 丢弃：currentPage=null url=$url" }
            } else {
                val existing = page.requests.firstOrNull { it.url == url }
                if (existing != null) {
                    existing.count++
                    if (blocked) existing.blocked = true
                    adBlockLog { "addUrl 累加 url=$url count=${existing.count} blocked=${existing.blocked}" }
                } else {
                    page.requests.add(AccessRequest(url, domain, count = 1, blocked = blocked, time = System.currentTimeMillis()))
                    adBlockLog { "addUrl 新增 url=$url page=${page.pageUrl} reqs=${page.requests.size}" }
                }
            }
        }
        maybeFlush()
    }

    /** 生成访问记录的只读快照（深拷贝，含命中次数/拦截标记），供 UI 线程安全地遍历展示 */
    fun snapshot(): List<AccessPage> = synchronized(recordLock) {
        val pages = accessPages.value
        adBlockLog { "snapshot pages=${pages?.size} reqs=${pages?.sumOf { it.requests.size }}" }
        pages?.map { p ->
            AccessPage(
                p.pageUrl, p.time, p.title, p.favicon,
                ArrayList(p.requests.map { AccessRequest(it.url, it.domain, it.count, it.blocked, it.time) }),
                p.loading
            )
        } ?: emptyList()
    }

    /** 清空访问记录（仅内存、不落库；UI 弹窗确认后调用），黑白名单规则不受影响 */
    fun clearAccessRecords() {
        adBlockLog { "clearAccessRecords 调用 pagesBefore=${accessPages.value?.size} currentPage=${currentPage?.pageUrl}" }
        synchronized(recordLock) {
            accessPages.value?.clear()
            currentPage = null
        }
        adBlockLog { "clearAccessRecords 完成 pagesAfter=${accessPages.value?.size}" }
        accessPages.postValue(accessPages.value)
    }

    /** UI 刷新节流：高频网络请求下避免每请求都重建适配器 */
    private fun maybeFlush() {
        val now = System.currentTimeMillis()
        if (now - lastFlush >= 350) {
            lastFlush = now
            accessPages.postValue(accessPages.value)
        }
    }

    /**
     * 通知访问记录列表重绘。访问记录里的“+白名单/-白名单”“+黑名单/-黑名单”状态
     * 取决于黑白名单，名单变化时必须同时通知，否则点了按钮当前列表不刷新，
     * 要切到别的 tab 再切回来（重新 setAdapter 触发重绑）才看得到变化。
     */
    private fun notifyAccessChanged() {
        accessPages.postValue(accessPages.value)
    }

    /** 黑/白名单规则变更后，按当前规则优先级重算访问记录里每条请求的“已拦截”标记 */
    private fun recomputeBlockedMarks() {
        val blackRules = blacklist.value?.toList().orEmpty()
        val whiteRules = whitelist.value?.toList().orEmpty()
        synchronized(recordLock) {
            accessPages.value?.forEach { page ->
                page.requests.forEach { r ->
                    // 被白名单放行的请求即使命中黑名单规则，也不能再显示“已拦截”
                    r.blocked = adBlockIsBlocked(
                        r.url, blackRules, whiteRules,
                        r.url.toHttpUrlOrNull()?.topPrivateDomain()
                    )
                }
            }
        }
        notifyAccessChanged()
    }

    // ===================== 黑名单 / 白名单（落库永久保存） =====================

    fun addBlackHost(host: String, mode: String = BLOCK_MODE_DOMAIN) {
        if (host.isBlank()) return
        // 黑白名单互斥：同一规则值不能同时存在，先撤掉对方的同值规则
        removeWhiteHost(HostStr(host, listOf()).apply { this.mode = mode })
        val list = blacklist.value ?: CopyOnWriteArrayList()
        if (list.none { it.host == host && it.mode == mode }) {
            list.add(0, HostStr(host, listOf()).apply { this.mode = mode })
            blacklist.postValue(list)
            persist("black", list)
        }
        // 按当前全部黑名单规则精确重算拦截标记：加「精确URL」规则只标记该 URL，
        // 不会像旧的按范围批量标记那样把整个顶级域名及其下所有 URL 都标成已拦截。
        recomputeBlockedMarks()
    }

    fun removeBlackHost(hostStr: HostStr) {
        val list = blacklist.value ?: CopyOnWriteArrayList()
        if (!list.removeAll { it.host == hostStr.host && it.mode == hostStr.mode }) return
        blacklist.postValue(list)
        persist("black", list)
        recomputeBlockedMarks()
    }

    /**
     * 误拦截恢复：让指定 URL 真正放行。
     * 1) 移除会拦截该 URL 的黑名单规则（顶级域名 / 子域名 / 精确URL 三档都尝试）；
     * 2) 加入一条“精确 URL”白名单——白名单在 shouldInterceptRequest 中优先于黑名单与跨域拦截，
     *    即使该资源是被跨域拦截模式（crossOriginMode==2）拦的也能放行；
     * 3) 同步把访问记录里对应请求的“已拦截”标记复位，使 UI 立即从“已拦截”变回正常。
     */
    fun restoreBlocked(url: String) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val top = domainKey(url)   // 域名取顶级，纯 IP 取 IP 本身
        val host = httpUrl.host
        val list = blacklist.value
        val hadTop = top != null && list?.any {
            parseRuleHost(it.host).first == top && it.mode == BLOCK_MODE_DOMAIN
        } == true
        val hadSub = list?.any {
            parseRuleHost(it.host).first == host && it.mode == BLOCK_MODE_SUBDOMAIN
        } == true
        if (top != null) removeBlackHost(HostStr(top, listOf()).apply { mode = BLOCK_MODE_DOMAIN })
        removeBlackHost(HostStr(host, listOf()).apply { mode = BLOCK_MODE_SUBDOMAIN })
        removeBlackHost(HostStr(url, listOf()).apply { mode = BLOCK_MODE_URL })
        // 加白名单（精确URL），保证该资源真正放行
        addWhiteHost(url, BLOCK_MODE_URL)
        // 复位访问记录里被误拦条目的标记
        synchronized(recordLock) {
            accessPages.value?.forEach { page ->
                page.requests.forEach { r ->
                    val unblock = r.url == url
                            || (hadSub && r.domain == host)
                            || (top != null && domainKey(r.domain) == top)
                    if (unblock) r.blocked = false
                }
            }
        }
        accessPages.postValue(accessPages.value)
    }

    fun addWhiteHost(host: String, mode: String = BLOCK_MODE_DOMAIN) {
        if (host.isBlank()) return
        // 黑白名单互斥：同一规则值不能同时存在，先撤掉对方的同值规则
        removeBlackHost(HostStr(host, listOf()).apply { this.mode = mode })
        val list = whitelist.value ?: CopyOnWriteArrayList()
        if (list.none { it.host == host && it.mode == mode }) {
            list.add(0, HostStr(host, listOf()).apply { this.mode = mode })
            whitelist.postValue(list)
            persist("white", list)
        }
        // 白名单优先，新增放行会改变“是否被拦截”的实际结果：
        // 同时重算访问记录标记并刷新按钮状态（否则当前列表无变化，要切 tab 回来重绑才显示）
        recomputeBlockedMarks()
    }

    fun removeWhiteHost(hostStr: HostStr) {
        val list = whitelist.value ?: CopyOnWriteArrayList()
        if (!list.removeAll { it.host == hostStr.host && it.mode == hostStr.mode }) return
        whitelist.postValue(list)
        persist("white", list)
        recomputeBlockedMarks()
    }

    /**
     * 批量移除规则，[entries] 为 null 时清空该类全部。
     * 用于「清空整个黑名单/白名单」与「按月清空」两个入口，删除后同步刷新访问记录标记。
     */
    private fun removeHosts(kind: String, entries: Collection<HostStr>?) {
        val live = if (kind == "black") blacklist else whitelist
        val list = live.value ?: return
        if (entries == null) {
            if (list.isEmpty()) return
            list.clear()
        } else {
            if (entries.isEmpty()) return
            val keys = entries.mapTo(HashSet()) { it.host to it.mode }
            if (!list.removeAll { (it.host to it.mode) in keys }) return
        }
        live.postValue(list)
        persist(kind, list)
        // 清空/按月删除黑白名单都会改变实际拦截结果，统一重算访问记录标记
        recomputeBlockedMarks()
    }

    /** 清空整个黑名单（UI 弹窗确认后调用） */
    fun clearBlack() = removeHosts("black", null)

    /** 清空整个白名单（UI 弹窗确认后调用） */
    fun clearWhite() = removeHosts("white", null)

    /** 按月份清空黑名单中的指定规则 */
    fun removeBlackHosts(entries: Collection<HostStr>) = removeHosts("black", entries)

    /** 按月份清空白名单中的指定规则 */
    fun removeWhiteHosts(entries: Collection<HostStr>) = removeHosts("white", entries)

    private fun AdBlockHostEntity.toHostStr(): HostStr {
        val type = runCatching {
            gson.fromJson(typeJson, Array<String>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
        return HostStr(host, type.toMutableList()).apply {
            isPage = this@toHostStr.isPage
            time = this@toHostStr.time
            mode = this@toHostStr.mode
            lastHitTime = this@toHostStr.lastHitTime
        }
    }
}
