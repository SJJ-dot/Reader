package com.sjianjun.reader.bean

class JsVersionInfo {
    var source: String = ""
    var version = 0
    /**
     * 是不是首发站
     */
    var starting = false
    var priority = 0
    override fun toString(): String {
        return "JsVersionInfo(source='$source', version=$version, starting=$starting, priority=$priority)"
    }

}