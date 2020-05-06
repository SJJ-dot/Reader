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
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Version

            if (fileName != other.fileName) return false
            if (version != other.version) return false
            if (starting != other.starting) return false
            if (priority != other.priority) return false
            if (supportBookCity != other.supportBookCity) return false

            return true
        }

        override fun hashCode(): Int {
            var result = fileName.hashCode()
            result = 31 * result + version
            result = 31 * result + starting.hashCode()
            result = 31 * result + priority
            result = 31 * result + supportBookCity.hashCode()
            return result
        }

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JsVersionInfo

        if (version != other.version) return false
        if (versions != other.versions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + (versions?.hashCode() ?: 0)
        return result
    }


}