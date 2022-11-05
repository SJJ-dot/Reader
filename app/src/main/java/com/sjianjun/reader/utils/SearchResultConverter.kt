package com.sjianjun.reader.utils

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.SearchResult

fun SearchResult.toBook(): Book {
    val book = Book()
    book.bookSourceId = bookSource?.id.toString()
    book.title = bookTitle
    book.author = bookAuthor
    book.cover = bookCover
    book.url = bookUrl
    return book
}

fun List<SearchResult>.toBookList(): List<Book> {
    return map { it.toBook() }
}