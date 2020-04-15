package com.sjianjun.reader.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val strIdMap = ConcurrentHashMap<String, Long>()
private val idCount = AtomicLong()
private val idCreator = { idCount.getAndIncrement() }
val String.id: Long
    get() = strIdMap.getOrPut(this, idCreator)