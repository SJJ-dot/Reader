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
import java.util.concurrent.CopyOnWriteArrayList

class HostStr(val host: String, var type: List<String>) {
    var isPage = false
    var time: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(System.currentTimeMillis())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HostStr

        return host == other.host
    }

    override fun hashCode(): Int {
        return host.hashCode()
    }

}

fun MutableLiveData<CopyOnWriteArrayList<HostStr>>?.contains(host: String?): Boolean {
    return this?.value?.any { it.host == host } == true
}

object AdBlock {
    private val dao get() = DbFactory.db.browserDataDao()
    private val gson = Gson()

    val blacklist = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())
    val whitelist = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())

    // 记录所有的url
    val hostList = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                AdBlockHostEntity(it.host, kind, gson.toJson(it.type), it.isPage, it.time)
            })
        }
    }

    fun addBlackHost(host: HostStr) {
        val list = blacklist.value ?: CopyOnWriteArrayList()
        if (!list.contains(host)) {
            list.add(0, host)
            blacklist.postValue(list)
            persist("black", list)
        }

        val mutableList = hostList.value as CopyOnWriteArrayList
        mutableList.removeAll { it.host == host.host }
        hostList.postValue(mutableList)
    }

    fun removeBlackHost(host: HostStr) {
        val mutableList = hostList.value as CopyOnWriteArrayList
        mutableList.add(host)
        mutableList.sortByDescending { it.time }
        hostList.postValue(mutableList)

        val list = blacklist.value as CopyOnWriteArrayList
        list.removeAll { it.host == host.host }
        blacklist.postValue(list)
        persist("black", list)
    }

    fun addWhiteHost(host: HostStr) {
        val list = whitelist.value ?: CopyOnWriteArrayList()
        if (!list.contains(host)) {
            list.add(0, host)
            whitelist.postValue(list)
            persist("white", list)
        }

        val mutableList = hostList.value as CopyOnWriteArrayList
        mutableList.removeAll { it.host == host.host }
        hostList.postValue(mutableList)
    }

    fun removeWhiteHost(host: HostStr) {
        val mutableList = hostList.value as CopyOnWriteArrayList
        mutableList.add(host)
        mutableList.sortByDescending { it.time }
        hostList.postValue(mutableList)

        val list = whitelist.value as CopyOnWriteArrayList
        list.removeAll { it.host == host.host }
        whitelist.postValue(list)
        persist("white", list)
    }

    @Synchronized
    fun addUrl(url: String) {
        val topHost = url.toHttpUrlOrNull()?.topPrivateDomain() ?: return
        if (topHost.isBlank() || blacklist.contains(topHost) || whitelist.contains(topHost)) {
            return
        }

        val type = url.split("?")[0].split("/").last().split(".").last()
        var hostStr = hostList.value?.firstOrNull { it.host == topHost }
        if (hostStr == null) {
            hostStr = HostStr(topHost, listOf())
            hostList.value?.add(0, hostStr)
            hostList.postValue(hostList.value)
        }
        if (type.isNotBlank() && type !in hostStr.type) {
            hostStr.type = hostStr.type.toMutableList().apply { add(type) }.sortedBy { it.length }
            hostList.postValue(hostList.value)
        }
    }

    fun markPage(url: String?) {
        val topHost = url?.toHttpUrlOrNull()?.topPrivateDomain() ?: return
        val hostStr = hostList.value?.firstOrNull { it.host == topHost } ?: return
        hostStr.isPage = true
        hostList.postValue(hostList.value)
    }

    private fun AdBlockHostEntity.toHostStr(): HostStr {
        val type = runCatching {
            gson.fromJson(typeJson, Array<String>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
        return HostStr(host, type.toMutableList()).apply {
            isPage = this@toHostStr.isPage
            time = this@toHostStr.time
        }
    }
}
