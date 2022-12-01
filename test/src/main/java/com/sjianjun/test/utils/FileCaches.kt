package com.sjianjun.test.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileCaches {
    private val cacheRoot = File("./cache/files/")

    init {
        cacheRoot.mkdirs()
    }

    @JvmStatic
    fun save(key: String, value: String) {
        FileOutputStream(File(cacheRoot, key.md5)).use {
            val writer = it.bufferedWriter()
            writer.write(value)
            writer.flush()
        }
    }


    fun get(key: String): String {
        return FileInputStream(File(cacheRoot, key.md5)).use {
            it.bufferedReader().readText()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        save("aaa", "vvv")
        println(get("aaa"))
    }
}