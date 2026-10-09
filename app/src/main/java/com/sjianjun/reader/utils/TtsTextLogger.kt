package com.sjianjun.reader.utils

import sjj.alog.Log

/**
 * 诊断用：打印真正送进语音引擎的文本。
 *
 * 每个段落打印三行（logcat 过滤关键字 `TTS_TEXT`）：
 * 1. 文本本体（换行/制表/全角空格转成可见形式）
 * 2. 开头若干字符的码点，用来发现不可见字符
 * 3. 只在该段含「非普通字符」时打印（控制符、零宽、BOM、私用区、符号图形、表情等），带码点与出现次数
 */
object TtsTextLogger {

    private const val TAG = "TTS_TEXT"

    /** 打印码点的字符个数 */
    private const val HEAD_LIMIT = 16

    /** 文本本体最多打印的字符数 */
    private const val BODY_LIMIT = 300

    fun log(text: String, index: Int? = null) {
        val prefix = prefix(index)
        if (text.isEmpty()) {
            Log.i("$TAG $prefix text 为空")
            return
        }
        val body = if (text.length <= BODY_LIMIT) text else text.take(BODY_LIMIT) + "…"
        Log.i("$TAG $prefix len=${text.length} text=[${escape(body)}]")
        Log.i("$TAG $prefix head码点=${codePoints(text)}")
        val unusual = unusualChars(text)
        if (unusual.isNotEmpty()) {
            Log.e("$TAG $prefix 非普通字符=${unusual.joinToString(" ")}")
        }
    }

    private fun prefix(index: Int?): String = if (index == null) "" else "[第${index + 1}段]"

    /** 把不可见字符转成可见形式，便于直接看日志 */
    private fun escape(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (c in text) {
            when {
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c == '\u3000' -> sb.append('␣')
                c.code < 0x20 || c.code == 0x7F -> sb.append("\\u%04X".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 开头若干字符的码点 */
    private fun codePoints(text: String): String {
        val count = minOf(text.length, HEAD_LIMIT)
        return (0 until count).joinToString(" ") { "U+%04X".format(text[it].code) }
    }

    /**
     * 统计不属于「常规正文」的字符（码点 + 次数）。
     * 常规正文 = ASCII 可打印 / 全角形式 / 常用符号区 / 各种 CJK 汉字与标点 / 空白
     */
    private fun unusualChars(text: String): List<String> {
        val counts = LinkedHashMap<Int, Int>()
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            if (!isCommon(codePoint)) {
                counts[codePoint] = (counts[codePoint] ?: 0) + 1
            }
            i += Character.charCount(codePoint)
        }
        return counts.entries.map { (codePoint, count) -> "U+%04X x%d".format(codePoint, count) }
    }

    private fun isCommon(codePoint: Int): Boolean = when {
        codePoint == ' '.code || codePoint == '\n'.code || codePoint == '\r'.code || codePoint == '\t'.code -> true
        codePoint == 0x3000 -> true                       // 全角空格
        codePoint in 0x20..0x7E -> true                   // ASCII 可打印
        codePoint in 0xA0..0x2FF -> true                  // 拉丁扩展 / 常用符号
        codePoint in 0x2E80..0x9FFF -> true               // CJK 部首、标点、汉字
        codePoint in 0x3400..0x4DBF -> true               // CJK 扩展 A
        codePoint in 0xF900..0xFAFF -> true               // CJK 兼容汉字
        codePoint in 0xFE30..0xFE4F -> true               // CJK 兼容标点
        codePoint in 0xFF00..0xFFEF -> true               // 全角/半角形式
        codePoint in 0x20000..0x2FA1F -> true             // CJK 扩展 B 及以后
        else -> false
    }
}
