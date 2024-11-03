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
            blacklist.observeForever {
                globalConfig.hostBlacklist = it
            }
            whitelist.observeForever {
                globalConfig.hostWhitelist = it
            }
        }

        fun addBlackHost(host: String) {
            if (blacklist.value?.contains(host) == true) {
                return
            }
            blacklist.value?.add(host)
            blacklist.postValue(blacklist.value)
        }

        fun removeBlackHost(host: String) {
            val list = blacklist.value!!.toMutableList()
            list.remove(host)
            blacklist.postValue(list)
        }

        fun addWhiteHost(host: String) {
            if (whitelist.value?.contains(host) == true) {
                return
            }
            whitelist.value?.add(host)
            whitelist.postValue(whitelist.value)
        }

        fun removeWhiteHost(host: String) {
            val list = whitelist.value!!.toMutableList()
            list.remove(host)
            whitelist.postValue(list)
        }
    }

    // 记录所有的url
    val hostList = MutableLiveData<MutableList<String>>(mutableListOf())

    init {
        blacklist.observe(owner) { list ->
            val mutableList = hostList.value!!.toMutableList()
            list.forEach { blackHost ->
                mutableList.removeAll { it.endsWith(blackHost) }
            }
            hostList.postValue(mutableList)
        }
        whitelist.observe(owner) { list ->
            val mutableList = hostList.value!!.toMutableList()
            list.forEach { whiteHost ->
                mutableList.removeAll { it.endsWith(whiteHost) }
            }
            hostList.postValue(mutableList)
        }
    }

    fun addUrl(url: String) {
        val topHost = url.toHttpUrlOrNull()?.topPrivateDomain() ?: return
        if (topHost.isBlank() || blacklist.value?.contains(topHost) == true) {
            return
        }
        val host = url.toHttpUrlOrNull()?.host ?: return
        if (host.isBlank() || hostList.value?.contains(host) == true || blacklist.value?.contains(host) == true) {
            return
        }
        if (hostList.value?.contains(topHost) == false) {
            hostList.value?.add(topHost)
        }
        hostList.value?.add(host)
        hostList.postValue(hostList.value)
    }


}