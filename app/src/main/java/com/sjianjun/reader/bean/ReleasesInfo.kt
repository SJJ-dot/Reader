package com.sjianjun.reader.bean

import com.sjianjun.reader.utils.AppInfoUtil
import kotlin.math.max

class ReleasesInfo {
    var channel: String? = null
    var lastVersion: String? = null
    var updateContent: String? = null
    var downloadApkUrl: String? = null

    operator fun compareTo(other: ReleasesInfo): Int {
        return compareTo(other.lastVersion ?: "0.0.0")
    }

    operator fun compareTo(otherVersion: String): Int {
        if (otherVersion == lastVersion) {
            return 0
        }
        val the = lastVersion!!.split(".").mapNotNull { it.toIntOrNull() }
        val others = otherVersion.split(".").mapNotNull { it.toIntOrNull() }
        (0..max(the.size, others.size)).forEach {
            val theN = the.getOrNull(it) ?: 0
            val otherN = others.getOrNull(it) ?: 0
            if (theN > otherN) {
                return 1
            }
            if (theN < otherN) {
                return -1
            }
        }
        return 0
    }

    fun isUpgradeable(otherVersion: String = AppInfoUtil.versionName()): Boolean {
        return compareTo(otherVersion) > 0
    }


    override fun toString(): String {
        return "ReleasesInfo(channel=$channel, localVersion=${AppInfoUtil.versionName()}, lastVersion=$lastVersion, updateContent=$updateContent, downloadApkUrl=$downloadApkUrl)"
    }


}