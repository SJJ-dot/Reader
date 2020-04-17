package com.sjianjun.reader.utils

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.SearchResult


fun key(bookTitle: String, bookAuthor: String): String {
    return "title:${bookTitle} author:${bookAuthor}"
}

val Book.key: String
    get() = key(title, author)

val SearchResult.key: String
    get() = key(bookTitle, bookAuthor)