package com.sjianjun.reader

import com.sjianjun.reader.bean.SourceUrl

const val BOOK_ID = "BOOK_ID"
const val BOOK_SOURCE_ID = "BOOK_SOURCE_ID"

const val BOOK_TITLE = "BOOK_TITLE"
const val BOOK_AUTO_REFRESH = "BOOK_AUTO_REFRESH"

const val URL_REPO = "https://github.com/SJJ-dot/Reader"
val URL_BOOK_SOURCE_DEF = SourceUrl(
    "https://raw.githubusercontent.com/SJJ-dot/Reader/BookSource/test/BookSource/default.json.gzip",
    "修仙书源"
)