package com.sjianjun.reader.utils

import android.content.ClipboardManager
import android.content.Context
import com.sjianjun.reader.App
import com.sjianjun.reader.bean.Book

import com.sjianjun.reader.bean.SearchResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

fun String?.copyToClipboard(toast: Boolean = true) {
    val str = this ?: return
    val clipboard = App.app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clipData = android.content.ClipData.newPlainText("text", str)
    clipboard?.setPrimaryClip(clipData)
    if (toast) {
        toast("已复制：${str}")
    }
}

private val strIdMap = ConcurrentHashMap<String, Long>()
private val idCount = AtomicLong()
private val idCreator = { idCount.getAndIncrement() }
val String.id: Long
    get() = strIdMap.getOrPut(this, idCreator)


val SearchResult.id: Long
    get() = "$bookTitle $bookAuthor ${bookSource?.id}".id


fun key(bookTitle: String): String {
    return "title:${bookTitle}"
}

val Book.key: String
    get() = key(title)

val SearchResult.key: String
    get() = key(bookTitle)