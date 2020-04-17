package com.sjianjun.reader.utils

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.JavaScript
import com.sjianjun.reader.bean.SearchResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val strIdMap = ConcurrentHashMap<String, Long>()
private val idCount = AtomicLong()
private val idCreator = { idCount.getAndIncrement() }
val String.id: Long
    get() = strIdMap.getOrPut(this, idCreator)


val Book.id: Long
    get() = "$title $author".id

val Chapter.id: Long
    get() = url.id


val JavaScript.id: Long
    get() = source.id

val SearchResult.id: Long
    get() = "$bookTitle $bookAuthor".id