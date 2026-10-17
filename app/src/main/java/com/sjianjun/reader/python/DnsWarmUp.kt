package com.sjianjun.reader.python

import com.sjianjun.reader.App
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.repository.DbFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

/**
 * 启动时后台预热 DNS：把书架书籍、订阅书源、书源 getSiteUrl 的域名提前解析进 Python 侧的 DNS 缓存，
 * 让用户真正发起第一个请求时不再等 DNS（弱网/地铁上尤其明显）。
 *
 * 纯后台执行（fire-and-forget），不阻塞启动，也不阻塞用户的脚本请求：
 * DNS 解析与脚本 exec 之间会释放 GIL，且 [warmUp] 全程跑在 [Dispatchers.IO] 上。
 */
object DnsWarmUp {

    private const val GET_SITE_URL_TIMEOUT_MS = 3000L

    fun start() {
        App.app.appScope.launch(Dispatchers.IO) {
            runCatching { warmUp() }
                .onFailure { Log.e("DNS预热失败", it) }
        }
    }

    private suspend fun warmUp() = withContext(Dispatchers.IO) {
        val hosts = collectHosts()
        if (hosts.isEmpty()) return@withContext
        val list = hosts.toList()
        val resolved = runCatching { execModule.callAttr("warm_up_dns", list).toInt() }
            .getOrElse { -1 }
        Log.i("DNS预热完成：域名 ${list.size} 个，解析 ${resolved} 条")
    }

    /** 收集所有需要预热的域名（去重、小写） */
    internal suspend fun collectHosts(): Set<String> {
        val hosts = HashSet<String>()
        // 书架书籍（检查更新/加载详情会请求这些域名）
        runCatching {
            DbFactory.db.bookDao().getAllReadingBook().first().forEach { book ->
                hostOf(book.url)?.let { hosts.add(it) }
            }
        }
        // 订阅书源地址（书源下载/更新）
        runCatching {
            DbFactory.db.sourceUrlDao().getAll().forEach { src ->
                hostOf(src.url)?.let { hosts.add(it) }
            }
        }
        // 书源域名：脚本书源没有结构化域名，用脚本自己的 getSiteUrl 拿
        runCatching {
            DbFactory.db.bookSourceDao().getAllBookSource().forEach { source ->
                if (!source.enable || source.lauanage == BookSource.Language.file) return@forEach
                // 没有实现 getSiteUrl 的书源跳过，避免无谓的脚本执行与报错日志
                if (!runCatching { source.hasFunction(BookSource.Func.getSiteUrl) }.getOrDefault(false)) {
                    return@forEach
                }
                val siteUrl = runCatching { withTimeoutOrNull(GET_SITE_URL_TIMEOUT_MS) { source.getSiteUrl() } }
                    .getOrNull()
                hostOf(siteUrl)?.let { hosts.add(it) }
            }
        }
        return hosts
    }

    /** 提取域名（小写）；没有 scheme 时按 https 补，避免 getSiteUrl 返回裸域名被忽略 */
    private fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val withScheme = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        return withScheme.toHttpUrlOrNull()?.host
    }
}
