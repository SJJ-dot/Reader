package com.sjianjun.reader.utils

import com.sjianjun.reader.bean.Chapter
import java.util.regex.Pattern

/**
 * 获取章节名。eg:第1章 序章 返回“序章”
 */
fun Chapter.name(): String = chapterNameOf(title ?: "")

/**
 * 从章节标题中剥离「第X章/节/回」等前缀，返回纯章节名。
 */
fun chapterNameOf(title: String): String {
    val t = title.trim()
    if (t.isEmpty()) {
        return t
    }
    listOf(
        "^.*第[0-9[一二三四五六七八九零十百千万]]+[章节回](.+$)",
        "^.*第[0-9[一二三四五六七八九零十百千万]]+(.+$)",
        "^.*[0-9[一二三四五六七八九零十百千万]]+[章节回](.+$)",
        "^.*[0-9[一二三四五六七八九零十百千万]]+(.+$)"
    ).map(Pattern::compile)
        .forEach {
            val matcher = it.matcher(t)
            if (matcher.find()) {
                return matcher.group(1)?.trim() ?: t
            }
        }
    return t
}

/**
 * 归一化章节名：去除「第X章」前缀、空白与常见标点，并将全角字符转半角，便于跨书源比对。
 */
fun String.normalizedChapterName(): String {
    val name = chapterNameOf(this)
    if (name.isEmpty()) return name
    val sb = StringBuilder(name.length)
    for (c in name) {
        when {
            c.isWhitespace() || c == '\u3000' -> Unit
            c in PUNCTUATION_CHARS -> Unit
            c in '\uFF01'..'\uFF5E' -> {
                // 全角转半角后再判断是否为标点
                val half = c - 0xFEE0
                if (half !in PUNCTUATION_CHARS) sb.append(half)
            }
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

private val PUNCTUATION_CHARS: Set<Char> = (
    "，。！？、；：“”‘’（）《》【】〈〉「」『』…—～·" +
        ",.!?;:\"'()[]{}<>/\\|-_+=@#\$%^&*"
    ).toSet()
