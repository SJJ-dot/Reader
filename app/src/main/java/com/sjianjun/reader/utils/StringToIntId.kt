package com.sjianjun.reader.utils

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.BookSource
import com.sjianjun.reader.bean.SearchResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val strIdMap = ConcurrentHashMap<String, Long>()
private val idCount = AtomicLong()
private val idCreator = { idCount.getAndIncrement() }
val String.id: Long
    get() = strIdMap.getOrPut(this, idCreator)


val SearchResult.id: Long
    get() = "$bookTitle $bookAuthor ${bookSource?.id}".id