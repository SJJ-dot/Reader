package com.sjianjun.reader.utils

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.SearchResult
import sjj.alog.Log
import java.util.regex.Pattern

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


/**
 * 获取章节名。eg:第1章 序章 返回“序章”
 */
fun Chapter.name(): String {
    val title = title?.trim() ?: return ""
    if (title.isEmpty()) {
        return title
    }
    listOf(
        "^第[0-9[一二三四五六七八九零十百千万]]+[章节回](.+$)",
        "^第[0-9[一二三四五六七八九零十百千万]]+(.+$)",
        "^[0-9[一二三四五六七八九零十百千万]]+[章节回](.+$)",
        "^[0-9[一二三四五六七八九零十百千万]]+(.+$)"
    ).map(Pattern::compile)
        .forEach {
            val matcher = it.matcher(title)
            if (matcher.find()) {
                return matcher.group(1)?.trim() ?: title
            }
        }
    return title
}