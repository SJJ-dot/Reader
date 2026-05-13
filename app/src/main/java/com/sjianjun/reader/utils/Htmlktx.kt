package com.sjianjun.reader.utils

import android.text.Html
import androidx.core.text.parseAsHtml


fun String?.format(indent: Boolean = false): CharSequence {
    this ?: return ""
    val html = this.replace("\n", "<br/>").parseAsHtml(Html.FROM_HTML_MODE_COMPACT)
    val parts = html.split(Regex("""\s{2,}|\\n|\r\n|\n|\r"""))
    val sb = StringBuilder()
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.isNotEmpty()) {
            if (indent) {
                sb.append("　　") // 两个全角空格
            }
            sb.append(trimmed).append("\n")
        }
    }
    return sb.toString().trimEnd()
}

fun String.htmlToSpanned(): CharSequence {
    return this.parseAsHtml(Html.FROM_HTML_MODE_COMPACT)
}

fun colorText(text: String, color: String): String {
    // color 支持 "#RRGGBB" 或 "red" 这类格式
    return "<font color=\"$color\">$text</font>"
}

fun colorText(text: String, color: Int): String {
    // 将颜色转换为十六进制字符串
    val hexColor = String.format("#%06X", 0xFFFFFF and color)
    return colorText(text, hexColor)
}

fun String?.colorText(target: String, color: Int): String? {
    this ?: return null
    val hexColor = String.format("#%06X", 0xFFFFFF and color)
    val regex = Regex(Regex.escape(target))
    return this.replace(regex) { matchResult ->
        colorText(matchResult.value, hexColor)
    }
}