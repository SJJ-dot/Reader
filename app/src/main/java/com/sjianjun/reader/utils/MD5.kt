package com.sjianjun.reader.utils

import com.sjianjun.reader.coroutine.withIo
import sjj.alog.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.math.BigInteger
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException


val CharSequence?.md5: String
    get() {
        if (this == null) {
            return ""
        }
        return encode(this)
    }

fun encode(text: CharSequence, charset: Charset = Charsets.UTF_8): String {
    try {
        //获取md5加密对象
        val instance: MessageDigest = MessageDigest.getInstance("MD5")
        //对字符串加密，返回字节数组
        val digest: ByteArray = instance.digest(text.toString().toByteArray(charset))
        val sb = StringBuffer()
        for (b in digest) {
            //获取低八位有效值
            //将整数转化为16进制
            val hexString = Integer.toHexString(b.toInt() and 0xff)
            if (hexString.length < 2) {
                //如果是一位的话，补0
                sb.append("0")
            }
            sb.append(hexString)
        }
        return sb.toString()

    } catch (e: NoSuchAlgorithmException) {
        e.printStackTrace()
    }

    return ""
}

suspend fun MD5(inputStream: InputStream): String? {
    try {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(1024)
        var len = inputStream.read(buffer)
        while (len != -1) {
            digest.update(buffer, 0, len)
            len = inputStream.read(buffer)
        }
        return BigInteger(digest.digest()).toString(16)
    } catch (e: Exception) {
        return null
    }
}

suspend fun MD5(file: File): String? {
    if (!file.isFile) {
        return null
    }
    return try {
        FileInputStream(file).use { MD5(it) }
    } catch (e: Exception) {
        null
    }
}