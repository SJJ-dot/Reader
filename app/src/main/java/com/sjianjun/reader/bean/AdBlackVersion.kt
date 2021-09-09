package com.sjianjun.reader.bean

class AdBlackVersion {
    var filterUrlVersion: Int = 0
    var sourceVersions: List<SourceVersion>? = null

    class SourceVersion {
        var source: String = ""
        var version: Int = 0
    }
}