package com.sjianjun.reader.bean

class JsVersionInfo {
    var version = 0
    var versions: List<Version>? = null

    class Version {
        var fileName: String = ""
        var version = 0
        /**
         * 是不是首发站
         */
        var starting = false
        var priority = 0
        var supportBookCity = false
    }
}