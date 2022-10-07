package com.sjianjun.reader.bean

import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.utils.CONTENT_TYPE_ANDROID
import kotlin.math.max

class ReleasesInfo {
    var tag_name: String = ""
    var name: String = ""
    var body: String = ""

    var prerelease = false

    var assets: List<Assets>? = null

    val apkAssets: Assets?
        get() = assets?.find { it.content_type == CONTENT_TYPE_ANDROID }

    val apkDownloadUrl: String?
        get() = apkAssets?.browser_download_url

    val isNewVersion:Boolean
        get() {
            if (BuildConfig.VERSION_NAME == tag_name) {
                return false
            }
            val split1 = tag_name.split(".").mapNotNull { it.toIntOrNull() }
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

    class Assets {

        var browser_download_url = ""
        var content_type = ""
        var download_count = ""

        /**
         * 码云返回文件名字段
         */
        var name = ""

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Assets

            if (browser_download_url != other.browser_download_url) return false
            if (content_type != other.content_type) return false
            if (download_count != other.download_count) return false
            if (name != other.name) return false

            return true
        }

        override fun hashCode(): Int {
            var result = browser_download_url.hashCode()
            result = 31 * result + content_type.hashCode()
            result = 31 * result + download_count.hashCode()
            result = 31 * result + name.hashCode()
            return result
        }

        override fun toString(): String {
            return "Assets(browser_download_url='$browser_download_url', content_type='$content_type', download_count='$download_count', name='$name')"
        }

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReleasesInfo

        if (tag_name != other.tag_name) return false
        if (name != other.name) return false
        if (body != other.body) return false
        if (prerelease != other.prerelease) return false
        if (assets != other.assets) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tag_name.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + body.hashCode()
        result = 31 * result + prerelease.hashCode()
        result = 31 * result + (assets?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "ReleasesInfo(tag_name='$tag_name', name='$name', body='$body', prerelease=$prerelease, assets=$assets)"
    }

}