package com.sjianjun.reader.bean

import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.utils.CONTENT_TYPE_ANDROID
import kotlin.math.max

class ReleasesInfo {
    var channel: String? = null
    var lastVersion: String? = null
    var updateContent: String? = null
    var downloadApkUrl: String? = null
    val isNewVersion: Boolean
        get() {
            if (BuildConfig.VERSION_NAME == lastVersion) {
                return false
            }
            val split1 = lastVersion!!.split(".").mapNotNull { it.toIntOrNull() }
            val split2 = BuildConfig.VERSION_NAME.split(".").mapNotNull { it.toIntOrNull() }
            (0..max(split1.size, split2.size)).forEach {
                val n1 = split1.getOrNull(it) ?: 0
                val n2 = split2.getOrNull(it) ?: 0
                if (n1 > n2) {
                    return true
                }
                if (n1 < n2) {
                    return false
                }
            }
            return false
        }

    override fun toString(): String {
        return "ReleasesInfo(channel=$channel, localVersion=${BuildConfig.VERSION_NAME}, lastVersion=$lastVersion, updateContent=$updateContent, downloadApkUrl=$downloadApkUrl)"
    }


}