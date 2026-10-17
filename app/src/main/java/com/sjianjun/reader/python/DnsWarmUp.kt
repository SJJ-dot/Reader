package com.sjianjun.reader.python

import com.sjianjun.reader.App
import com.sjianjun.reader.repository.DbFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log

/**
 * 启动时后台预热 DNS：把书架书籍、订阅书源里的域名提前解析进 Python 侧的 DNS 缓存，
 * 让用户真正发起第一个请求时不再等 DNS（弱网/地铁上尤其明显）。
 */
object DnsWarmUp {

    private const val MAX_HOSTS = 30

    fun start() {
        App.app.appScope.launch(Dispatchers.IO) {
            runCatching { warmUp() }
                .onFailure { Log.e("DNS预热失败", it) }
        }
    }

    private suspend fun warmUp() = withContext(Dispatchers.IO) {
        val hosts = HashSet<String>()
        // 书架书籍（检查更新/加载详情会请求这些域名）
        runCatching {
            DbFactory.db.bookDao().getAllReadingBook().first().forEach { book ->
                book.url.toHttpUrlOrNull()?.host?.let { hosts.add(it) }
            }
        }
        // 订阅书源地址（书源下载/更新）
        runCatching {
            DbFactory.db.sourceUrlDao().getAll().forEach { src ->
                src.url.toHttpUrlOrNull()?.host?.let { hosts.add(it) }
            }
        }
        if (hosts.isEmpty()) return@withContext
        val list = hosts.take(MAX_HOSTS).toList()
        val resolved = runCatching { execModule.callAttr("warm_up_dns", list).toInt() }
            .getOrElse { -1 }
        Log.i("DNS预热完成：域名 ${list.size} 个，解析 ${resolved} 条")
    }
}
