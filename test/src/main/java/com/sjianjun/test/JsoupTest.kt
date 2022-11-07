package com.sjianjun.test

import com.sjianjun.test.utils.FileCaches
import org.jsoup.Jsoup

fun main(args: Array<String>) {
    val document = Jsoup.parse(FileCaches.get())
    document.tagName()
}