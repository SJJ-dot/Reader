package com.sjianjun.reader.utils

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

fun ByteArray.zip(): ByteArray {
    val buffer = ByteArrayOutputStream()
    val gzip = GZIPOutputStream(buffer)
    gzip.write(this)
    gzip.flush()
    return buffer.toByteArray()
}

fun ByteArray.unzip(): ByteArray {
    val buffer = ByteArrayOutputStream()
    val gzip = GZIPOutputStream(buffer)
    gzip.write(this)
    gzip.flush()
    return buffer.toByteArray()
}


fun String.zip(): ByteArray {
    return toByteArray().zip()
}