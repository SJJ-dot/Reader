package com.sjianjun.reader.utils

import android.text.Html


fun String?.format(indent: Boolean = false): CharSequence {
    this ?: return ""
    val html = Html.fromHtml(this.replace("\n", "<br/>"), Html.FROM_HTML_MODE_COMPACT)
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
    return Html.fromHtml(this, Html.FROM_HTML_MODE_COMPACT)
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