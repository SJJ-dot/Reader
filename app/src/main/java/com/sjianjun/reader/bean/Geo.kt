package com.sjianjun.reader.bean

data class Location(val lat: Double, val lon: Double)
data class GeoAddress(var formatted_address: String = "")

data class GeoResponse(
    var result: GeoAddress? = null,
    var msg: String = "",
    var status: String = ""
)