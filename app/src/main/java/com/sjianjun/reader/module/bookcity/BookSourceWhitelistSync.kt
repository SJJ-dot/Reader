package com.sjianjun.reader.module.bookcity

import com.tencent.mmkv.MMKV

/**
 * 书源域名白名单同步：
 * - 新增书源域名 → 加入白名单；
 * - 书源被删除/改域名 → 从白名单移除旧的域名。
 */
object BookSourceWhitelistSync {

    private val mmkv = MMKV.mmkvWithID("BrowserAdBlock")
    private const val KEY_HOSTS = "book_source_hosts"

    fun sync(adBlock: AdBlock, currentHosts: Collection<String>) {
        val oldHosts = mmkv.decodeStringSet(KEY_HOSTS)?.toSet().orEmpty()
        val newHosts = currentHosts.filter { it.isNotBlank() }.toSet()

        (newHosts - oldHosts).forEach { host ->
            adBlock.addWhiteHost(HostStr(host, listOf()))
        }
        (oldHosts - newHosts).forEach { host ->
            adBlock.removeWhiteHost(HostStr(host, listOf()))
        }
        if (oldHosts != newHosts) {
            mmkv.encode(KEY_HOSTS, newHosts)
        }
    }
}
