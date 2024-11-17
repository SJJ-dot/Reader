package com.sjianjun.reader.utils

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.SearchResult


fun key(bookTitle: String): String {
    return "title:${bookTitle}"
}

val Book.key: String
    get() = key(title)

val SearchResult.key: String
    get() = key(bookTitle)