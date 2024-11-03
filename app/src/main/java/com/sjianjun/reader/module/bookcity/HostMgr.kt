package com.sjianjun.reader.module.bookcity

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.sjianjun.reader.preferences.globalConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class HostMgr(owner: LifecycleOwner) {
    companion object {
        val blacklist = MutableLiveData<MutableList<String>>(mutableListOf())
        val whitelist = MutableLiveData<MutableList<String>>(mutableListOf())

        init {
            blacklist.postValue(globalConfig.hostBlacklist)
            whitelist.postValue(globalConfig.hostWhitelist)
        }

        fun addBlackHost(host: String) {
            if (blacklist.value?.contains(host) == true) {
                return
            }
            blacklist.value?.add(host)
            blacklist.postValue(blacklist.value)
        }

        fun removeBlackHost(host: String) {
            blacklist.value?.remove(host)
            blacklist.postValue(blacklist.value)
        }

        fun addWhiteHost(host: String) {
            if (whitelist.value?.contains(host) == true) {
                return
            }
            whitelist.value?.add(host)
            whitelist.postValue(whitelist.value)
        }

        fun removeWhiteHost(host: String) {
            whitelist.value?.remove(host)
            whitelist.postValue(whitelist.value)
        }
    }

    // 记录所有的url
    val hostList = MutableLiveData<MutableList<String>>(mutableListOf())

    init {
        blacklist.observe(owner) { list ->
            list.forEach { blackHost ->
                hostList.value?.removeAll { it.endsWith(blackHost) }
            }
            globalConfig.hostBlacklist = list
        }
        whitelist.observe(owner) { list ->
            list.forEach { whiteHost ->
                hostList.value?.removeAll { it.endsWith(whiteHost) }
            }
            globalConfig.hostWhitelist = list
        }
    }

    fun addUrl(url: String) {
        val topHost = url.toHttpUrlOrNull()?.topPrivateDomain() ?: return
        if (topHost.isBlank() || hostList.value?.contains(topHost) == true || blacklist.value?.contains(topHost) == true) {
            return
        }
        val host = url.toHttpUrlOrNull()?.host ?: return
        if (host.isBlank() || hostList.value?.contains(host) == true || blacklist.value?.contains(host) == true) {
            return
        }
        if (topHost != host) {
            hostList.value?.add(topHost)
        }
        hostList.value?.add(host)
        hostList.postValue(hostList.value)
    }


}