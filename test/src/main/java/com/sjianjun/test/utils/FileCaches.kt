package com.sjianjun.test.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileCaches {
    private val cacheRoot = File("./test/cache/")

    init {
        cacheRoot.mkdirs()
    }

    @JvmStatic
    fun save(value: String) {
        FileOutputStream(File(cacheRoot, "test.html")).use {
            val writer = it.bufferedWriter()
            writer.write(value)
            writer.flush()
        }
    }


    fun get(): String {
        return FileInputStream(File(cacheRoot, "test.html")).use {
            it.bufferedReader().readText()
        }
    }
}