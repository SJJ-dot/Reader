package com.sjianjun.reader.bean

class ReleasesInfo {
    var tag_name: String = ""
    var name: String = ""
    var body: String = ""

    var prerelease = false

    var assets: List<Assets>? = null

    val apkAssets: Assets?
        get() = assets?.find { it.name.endsWith(".apk") }

    val apkDownloadUrl: String?
        get() = apkAssets?.browser_download_url

    class Assets {

        var browser_download_url = ""
        var content_type = ""
        var download_count = ""
        /**
         * 码云返回文件名字段
         */
        var name = ""
    }
}