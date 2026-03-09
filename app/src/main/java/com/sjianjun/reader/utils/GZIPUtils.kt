package com.sjianjun.reader.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun ByteArray.zip(): ByteArray {
    val buffer = ByteArrayOutputStream()
    GZIPOutputStream(buffer).use { gzip ->
        gzip.write(this)
        gzip.finish()
    }
    return buffer.toByteArray()
}

fun ByteArray.unzip(): ByteArray {
    val input = ByteArrayInputStream(this)
    val buffer = ByteArrayOutputStream()
    GZIPInputStream(input).use { gzipIn ->
        val tmp = ByteArray(1024)
        var read: Int
        while (gzipIn.read(tmp).also { read = it } != -1) {
            buffer.write(tmp, 0, read)
        }
    }
    return buffer.toByteArray()
}


fun String.zip(): ByteArray {
    return toByteArray().zip()
}