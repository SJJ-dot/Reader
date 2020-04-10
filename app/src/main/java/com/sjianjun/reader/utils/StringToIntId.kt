package com.sjianjun.reader.utils

import java.util.zip.CRC32

private val CRC32 = ThreadLocal.withInitial { CRC32() }

val String.id:Long
    get() {
        val crC32 = CRC32.get()!!
        crC32.reset()
        crC32.update(this.toByteArray())
        return this.hashCode().toLong() shl 32 or crC32.value
    }