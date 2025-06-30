package com.sjianjun.reader.module.bookcity

import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.preferences.AppConfig
import com.sjianjun.reader.utils.md5
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class HostStr(val host: String) {
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
    private val  config = AppConfig("BookCity-" + url?.toHttpUrlOrNull()?.host)

    val blacklist = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())
    val whitelist = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())

    // 记录所有的url
    val hostList = MutableLiveData<CopyOnWriteArrayList<HostStr>>(CopyOnWriteArrayList())

    init {
        blacklist.postValue(CopyOnWriteArrayList(config.hostBlacklist))
        whitelist.postValue(CopyOnWriteArrayList(config.hostWhitelist))
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

    fun addWhiteHost(host: HostStr) {
        if (whitelist.value?.contains(host) == true) {
            return
        }
        whitelist.value?.add(host)
        whitelist.postValue(whitelist.value)
        config.hostWhitelist = whitelist.value ?: mutableListOf()

        val mutableList = hostList.value as CopyOnWriteArrayList
        mutableList.removeAll { it.host.endsWith(host.host) }
        hostList.postValue(mutableList)
    }

    fun removeWhiteHost(host: HostStr) {
        val list = whitelist.value as CopyOnWriteArrayList
        list.remove(host)
        whitelist.postValue(list)
        config.hostWhitelist = whitelist.value ?: mutableListOf()

        val mutableList = hostList.value as CopyOnWriteArrayList
        mutableList.add(host)
        mutableList.sortBy { it.time }
        hostList.postValue(mutableList)
    }

    fun addUrl(url: String) {
        val topHost = url.toHttpUrlOrNull()?.topPrivateDomain() ?: return
        if (topHost.isBlank() || blacklist.contains(topHost) || whitelist.contains(topHost)) {
            return
        }
        val host = url.toHttpUrlOrNull()?.host ?: return
        if (host.isBlank() || hostList.contains(host) || blacklist.contains(host) || whitelist.contains(host)) {
            return
        }
        if (!hostList.contains(topHost)) {
            hostList.value?.add(HostStr(topHost))
        }
        hostList.value?.add(HostStr(host))
        hostList.postValue(hostList.value)
    }
}