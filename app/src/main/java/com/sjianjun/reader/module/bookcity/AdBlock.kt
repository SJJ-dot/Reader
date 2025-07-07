package com.sjianjun.reader.module.bookcity

import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.preferences.AppConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sjj.alog.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class HostStr(val host: String, var type: List<String>) {
    val time: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(System.currentTimeMillis())
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

class AdBlock(url: String?) {
    private val config = AppConfig("ad-" + (url?.toHttpUrlOrNull()?.topPrivateDomain() ?: url?.toHttpUrlOrNull()?.host ?: url))

    val blacklist = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())

    // 记录所有的url
    val hostList = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())

    init {
        blacklist.postValue(CopyOnWriteArrayList(config.hostBlacklist))
    }

    fun addBlackHost(host: HostStr) {
        if (blacklist.value?.contains(host) == true) {
            return
        }
        blacklist.value?.add(host)
        blacklist.postValue(blacklist.value)
        config.hostBlacklist = blacklist.value ?: mutableListOf()

        val mutableList = hostList.value as CopyOnWriteArrayList
        mutableList.removeAll { it.host.endsWith(host.host) }
        hostList.postValue(mutableList)
    }

    fun removeBlackHost(host: HostStr) {
        val list = blacklist.value as CopyOnWriteArrayList
        list.remove(host)
        blacklist.postValue(list)
        config.hostBlacklist = blacklist.value ?: mutableListOf()

        val mutableList = hostList.value as CopyOnWriteArrayList
        mutableList.add(host)
        mutableList.sortBy { it.time }
        hostList.postValue(mutableList)
    }

    fun addUrl(url: String) {
        val topHost = url.toHttpUrlOrNull()?.topPrivateDomain() ?: return
        if (topHost.isBlank() || blacklist.contains(topHost)) {
            return
        }
        val type = url.split("?")[0].split("/").last().split(".").last()
        var hostStr = hostList.value?.firstOrNull { it.host == topHost }
        if (hostStr == null) {
            hostStr = HostStr(topHost, listOf())
            hostList.value?.add(hostStr)
            hostList.postValue(hostList.value)
        }
        if (type.isNotBlank() && type !in hostStr.type) {
            hostStr.type = hostStr.type.toMutableList().apply { add(type) }
            hostList.postValue(hostList.value)
        }
    }
}
