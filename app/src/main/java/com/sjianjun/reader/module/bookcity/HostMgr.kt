package com.sjianjun.reader.module.bookcity

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.preferences.globalConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Locale

class HostStr(val host: String) {
    val time: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(System.currentTimeMillis())
}

fun MutableLiveData<MutableList<HostStr>>?.contains(host: String?): Boolean {
    return this?.value?.any { it.host == host } == true
}

class HostMgr(owner: LifecycleOwner) {
    // 记录所有的url
    val hostList = MutableLiveData<MutableList<HostStr>>(mutableListOf())

    init {
        blacklist.observe(owner) { list ->
            val mutableList = hostList.value!!.toMutableList()
            list.forEach { blackHost ->
                mutableList.removeAll { it.host.endsWith(blackHost.host) }
            }
            hostList.postValue(mutableList)
        }
        whitelist.observe(owner) { list ->
            val mutableList = hostList.value!!.toMutableList()
            list.forEach { whiteHost ->
                mutableList.removeAll { it.host.endsWith(whiteHost.host) }
            }
            hostList.postValue(mutableList)
        }
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


    companion object {
        val blacklist = MutableLiveData<MutableList<HostStr>>(mutableListOf())
        val whitelist = MutableLiveData<MutableList<HostStr>>(mutableListOf())

        init {
            blacklist.postValue(globalConfig.hostBlacklist)
            whitelist.postValue(globalConfig.hostWhitelist)
            blacklist.observeForever {
                globalConfig.hostBlacklist = it
            }
            whitelist.observeForever {
                globalConfig.hostWhitelist = it
            }
        }

        fun addBlackHost(host: HostStr) {
            if (blacklist.value?.contains(host) == true) {
                return
            }
            blacklist.value?.add(host)
            blacklist.postValue(blacklist.value)
        }

        fun removeBlackHost(host: HostStr) {
            val list = blacklist.value!!.toMutableList()
            list.remove(host)
            blacklist.postValue(list)
        }

        fun addWhiteHost(host: HostStr) {
            if (whitelist.value?.contains(host) == true) {
                return
            }
            whitelist.value?.add(host)
            whitelist.postValue(whitelist.value)
        }

        fun removeWhiteHost(host: HostStr) {
            val list = whitelist.value!!.toMutableList()
            list.remove(host)
            whitelist.postValue(list)
        }
    }
}