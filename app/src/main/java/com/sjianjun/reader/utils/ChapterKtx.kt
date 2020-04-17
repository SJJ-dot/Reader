package com.sjianjun.reader.utils

import com.sjianjun.reader.bean.Chapter
import java.util.regex.Pattern

/**
 * 获取章节名。eg:第1章 序章 返回“序章”
 */
fun Chapter.name(): String {
    val title = title?.trim() ?: return ""
    if (title.isEmpty()) {
        return title
    }
    listOf(
        "^.*第[0-9[一二三四五六七八九零十百千万]]+[章节回](.+$)",
        "^.*第[0-9[一二三四五六七八九零十百千万]]+(.+$)",
        "^.*[0-9[一二三四五六七八九零十百千万]]+[章节回](.+$)",
        "^.*[0-9[一二三四五六七八九零十百千万]]+(.+$)"
    ).map(Pattern::compile)
        .forEach {
            val matcher = it.matcher(title)
            if (matcher.find()) {
                return matcher.group(1)?.trim() ?: title
            }
        }
    return title
}