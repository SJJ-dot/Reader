package com.sjianjun.reader.module.bookcity

import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.preferences.AppConfig
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

class AdBlock() {
    private val config = AppConfig("ad-blacklist1")

    val blacklist = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())

    // 记录所有的url
    val hostList = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())

    init {
        blacklist.postValue(CopyOnWriteArrayList(config.hostBlacklist))
    }

    fun addBlackHost(host: HostStr) {
        if (blacklist.value?.contains(host) == false) {
            blacklist.value?.add(0,host)
            blacklist.postValue(blacklist.value)
            config.hostBlacklist = blacklist.value ?: mutableListOf()
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
        config.hostBlacklist = blacklist.value ?: mutableListOf()
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
            hostList.value?.add(0,hostStr)
            hostList.postValue(hostList.value)
        }
        if (type.isNotBlank() && type !in hostStr.type) {
            hostStr.type = hostStr.type.toMutableList().apply { add(type) }.sortedBy { it.length }
            hostList.postValue(hostList.value)
        }
    }

    fun markPage(url: String?) {
        val topHost = url?.toHttpUrlOrNull()?.topPrivateDomain() ?: return
        val hostStr = hostList.value?.firstOrNull { it.host == topHost }?:return
        hostStr.isPage = true
        hostList.postValue(hostList.value)
    }
}
