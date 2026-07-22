package com.sjianjun.reader.utils

import java.io.InputStream
import java.security.MessageDigest
import kotlin.concurrent.getOrSet

// 与 io.legado.app.utils.MD5Utils 行为一致（hutool digestHex 同为小写 hex），
// 用 JDK MessageDigest 实现，避免引入 hutool 依赖。
object MD5Utils {

    private val threadLocal = ThreadLocal<MessageDigest>()

    private val md5: MessageDigest
        get() = threadLocal.getOrSet { MessageDigest.getInstance("MD5") }

    fun md5Encode(str: String?): String {
        return md5.digest((str ?: "").toByteArray(Charsets.UTF_8)).toHex()
    }

    fun md5Encode(inputStream: InputStream): String {
        val digest = md5
        val buffer = ByteArray(8192)
        inputStream.use { input ->
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
