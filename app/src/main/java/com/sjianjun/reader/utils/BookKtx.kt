package com.sjianjun.reader.utils

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.SearchResult

fun List<Book>?.toGroup(map: MutableMap<String, MutableList<Book>> = mutableMapOf()): MutableMap<String, MutableList<Book>> {
    this?.forEach {
        val list = map.getOrPut(key(it.title, it.author)) { mutableListOf() }
        list.add(it)
    }
    return map
}

fun List<SearchResult>.toBookGroup(map: MutableMap<String, MutableList<SearchResult>> = mutableMapOf()) {
    forEach {
        val list = map.getOrPut(key(it.bookTitle, it.bookAuthor)) { mutableListOf() }
        list.add(it)
    }
}


fun key(bookTitle: String, bookAuthor: String): String {
    return "title:${bookTitle} author:${bookAuthor}"
}