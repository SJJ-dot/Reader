package com.sjianjun.reader.module.bookcity

import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.sjianjun.reader.bean.AdBlockHostEntity
import com.sjianjun.reader.repository.DbFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
    var blocked: Boolean = false
)

/** 一个网页（主框架）的访问记录：含该网页请求到的所有 URL */
data class AccessPage(
    val pageUrl: String,
    var time: Long,
    var title: String = "",
    var favicon: String = "",
    val requests: MutableList<AccessRequest> = mutableListOf()
)

object AdBlock {
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

    private fun persist(kind: String, list: List<HostStr>) {
        scope.launch {
            dao.replaceAdBlock(kind, list.map {
                AdBlockHostEntity(it.host, kind, gson.toJson(it.type), it.isPage, it.time, it.mode)
            })
        }
    }

    // ===================== 匹配（支持 顶级域名 / 子域名 / 精确URL） =====================

    /** 该 URL 是否命中黑名单（命中会累计该规则命中次数） */
    fun shouldBlock(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        val top = httpUrl.topPrivateDomain()
        val host = httpUrl.host
        val matched = blacklist.value?.firstOrNull { e ->
            when (e.mode) {
                BLOCK_MODE_URL -> e.host == url
                BLOCK_MODE_SUBDOMAIN -> e.host == host
                else -> e.host == top
            }
        }
        if (matched != null) recordHit(matched.host, matched.mode)
        return matched != null
    }

    /** 该 URL 是否命中白名单（命中会累计该规则命中次数） */
    fun shouldAllow(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        val top = httpUrl.topPrivateDomain()
        val host = httpUrl.host
        val matched = whitelist.value?.firstOrNull { e ->
            when (e.mode) {
                BLOCK_MODE_URL -> e.host == url
                BLOCK_MODE_SUBDOMAIN -> e.host == host
                else -> e.host == top
            }
        }
        if (matched != null) recordHit(matched.host, matched.mode)
        return matched != null
    }

    // ===================== 命中次数统计（内存，重启清零） =====================

    private val hitCounts = ConcurrentHashMap<String, Int>()

    private fun hitKey(host: String, mode: String) = "$host#$mode"

    /** 累计某条黑白名单规则的命中次数 */
    private fun recordHit(host: String, mode: String) {
        val key = hitKey(host, mode)
        hitCounts[key] = (hitCounts[key] ?: 0) + 1
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
    private fun topPrivateOf(hostOrUrl: String): String? =
        hostOrUrl.toHttpUrlOrNull()?.topPrivateDomain()
            ?: "https://$hostOrUrl".toHttpUrlOrNull()?.topPrivateDomain()

    // ===================== 访问记录（内存，不落库） =====================
    // shouldInterceptRequest 会在 WebView 的多个线程并发回调，
    // 因此所有对访问记录的读写都用 recordLock 串行化，避免 ConcurrentModificationException。

    /** 标记一个新的网页访问（主框架加载），作为后续请求的归属分组 */
    fun markPage(url: String?) {
        if (url.isNullOrBlank()) return
        synchronized(recordLock) {
            val list = accessPages.value ?: mutableListOf()
            var page = list.firstOrNull { it.pageUrl == url }
            if (page == null) {
                page = AccessPage(url, System.currentTimeMillis(), favicon = faviconOf(url))
                list.add(0, page)
            } else {
                list.remove(page)
                page.time = System.currentTimeMillis()
                list.add(0, page)
            }
            currentPage = page
        }
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
     * - 命中白名单：放行，不计入访问记录；
     * - 命中黑名单：仍计入访问记录并标记 blocked=true（累计命中次数，便于误拦截时恢复）；
     * - 其余：正常计入访问记录。
     */
    fun addUrl(url: String) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        if (shouldAllow(url)) return
        val blocked = shouldBlock(url)
        val domain = httpUrl.host
        synchronized(recordLock) {
            currentPage?.let { page ->
                val existing = page.requests.firstOrNull { it.url == url }
                if (existing != null) {
                    existing.count++
                    if (blocked) existing.blocked = true
                } else {
                    page.requests.add(AccessRequest(url, domain, count = 1, blocked = blocked))
                }
            }
        }
        maybeFlush()
    }

    /**
     * 加入黑名单后，把访问记录里命中的请求标记为“已拦截”（保留记录用于误拦截恢复），而非删除。
     * 标记为 blocked 的请求在访问记录中显示红色“已拦截”与“恢复”按钮。
     */
    fun markBlockedInAccess(hostOrUrl: String, mode: String) {
        val top = topPrivateOf(hostOrUrl)
        synchronized(recordLock) {
            accessPages.value?.forEach { page ->
                page.requests.forEach { r ->
                    val match = when (mode) {
                        BLOCK_MODE_URL -> r.url == hostOrUrl
                        BLOCK_MODE_SUBDOMAIN -> r.domain == hostOrUrl
                        else -> top != null && topPrivateOf(r.domain) == top
                    }
                    if (match) r.blocked = true
                }
            }
        }
        accessPages.postValue(accessPages.value)
    }

    /** 移除黑名单规则后，复位访问记录里命中该规则的“已拦截”标记（用于域名行 -黑名单） */
    fun unmarkBlockedInAccess(hostOrUrl: String, mode: String) {
        val top = topPrivateOf(hostOrUrl)
        synchronized(recordLock) {
            accessPages.value?.forEach { page ->
                page.requests.forEach { r ->
                    val match = when (mode) {
                        BLOCK_MODE_URL -> r.url == hostOrUrl
                        BLOCK_MODE_SUBDOMAIN -> r.domain == hostOrUrl
                        else -> top != null && topPrivateOf(r.domain) == top
                    }
                    if (match) r.blocked = false
                }
            }
        }
        accessPages.postValue(accessPages.value)
    }

    /** 生成访问记录的只读快照（深拷贝，含命中次数/拦截标记），供 UI 线程安全地遍历展示 */
    fun snapshot(): List<AccessPage> = synchronized(recordLock) {
        accessPages.value?.map { p ->
            AccessPage(
                p.pageUrl, p.time, p.title, p.favicon,
                ArrayList(p.requests.map { AccessRequest(it.url, it.domain, it.count, it.blocked) })
            )
        } ?: emptyList()
    }

    /** UI 刷新节流：高频网络请求下避免每请求都重建适配器 */
    private fun maybeFlush() {
        val now = System.currentTimeMillis()
        if (now - lastFlush >= 350) {
            lastFlush = now
            accessPages.postValue(accessPages.value)
        }
    }

    // ===================== 黑名单 / 白名单（落库永久保存） =====================

    fun addBlackHost(host: String, mode: String = BLOCK_MODE_DOMAIN) {
        if (host.isBlank()) return
        val list = blacklist.value ?: CopyOnWriteArrayList()
        if (list.none { it.host == host && it.mode == mode }) {
            list.add(0, HostStr(host, listOf()).apply { this.mode = mode })
            blacklist.postValue(list)
            persist("black", list)
        }
        markBlockedInAccess(host, mode)
    }

    fun removeBlackHost(hostStr: HostStr) {
        val list = blacklist.value ?: CopyOnWriteArrayList()
        list.removeAll { it.host == hostStr.host && it.mode == hostStr.mode }
        blacklist.postValue(list)
        persist("black", list)
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
        val top = httpUrl.topPrivateDomain()
        val host = httpUrl.host
        val list = blacklist.value
        val hadTop = top != null && list?.any { it.host == top && it.mode == BLOCK_MODE_DOMAIN } == true
        val hadSub = list?.any { it.host == host && it.mode == BLOCK_MODE_SUBDOMAIN } == true
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
                            || (top != null && topPrivateOf(r.domain) == top)
                    if (unblock) r.blocked = false
                }
            }
        }
        accessPages.postValue(accessPages.value)
    }

    fun addWhiteHost(host: String, mode: String = BLOCK_MODE_DOMAIN) {
        if (host.isBlank()) return
        val list = whitelist.value ?: CopyOnWriteArrayList()
        if (list.none { it.host == host && it.mode == mode }) {
            list.add(0, HostStr(host, listOf()).apply { this.mode = mode })
            whitelist.postValue(list)
            persist("white", list)
        }
    }

    fun removeWhiteHost(hostStr: HostStr) {
        val list = whitelist.value ?: CopyOnWriteArrayList()
        list.removeAll { it.host == hostStr.host && it.mode == hostStr.mode }
        whitelist.postValue(list)
        persist("white", list)
    }

    private fun AdBlockHostEntity.toHostStr(): HostStr {
        val type = runCatching {
            gson.fromJson(typeJson, Array<String>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
        return HostStr(host, type.toMutableList()).apply {
            isPage = this@toHostStr.isPage
            time = this@toHostStr.time
            mode = this@toHostStr.mode
        }
    }
}
