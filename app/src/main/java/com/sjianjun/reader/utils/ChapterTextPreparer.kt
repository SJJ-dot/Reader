package com.sjianjun.reader.utils

import com.sjianjun.reader.bean.Book
import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.bean.ReplacementRule
import com.sjianjun.reader.repository.ReplacementRuleUseCase

/**
 * 章节标题 / 正文的文字处理，阅读页与朗读服务共用，保证「看到的」和「听到的」完全一致。
 *
 * 完整流程与阅读页一致：
 * 1. HTML 转纯文本：调用方先做 `chapter.content.joinToString("\n") { it.format() }`
 * 2. 全角规范化（[normalizeText]，按「全角转换」开关）
 * 3. 简繁转换（按「简繁转换」设置）
 * 4. 净化（替换）规则；正文先按行加缩进再应用规则，保证行首类规则表现一致
 */
object ChapterTextPreparer {

    /** 简繁转换：关闭 */
    const val JIAN_FAN_OFF = 0

    /** 简繁转换：简体转繁体 */
    const val JIAN_TO_FAN = 1

    /** 简繁转换：繁体转简体 */
    const val FAN_TO_JIAN = 2

    /** 正文按行加缩进后再应用规则，与阅读页排版保持一致 */
    private const val PARAGRAPH_INDENT = "\u3000\u3000"

    fun title(
        title: String?,
        rules: List<ReplacementRule>,
        book: Book?,
        chapter: Chapter?,
        jianFanMode: Int,
        fullWidthConvert: Boolean,
    ): String {
        val converted = convert(title.orEmpty(), jianFanMode, fullWidthConvert)
        return applyRules(converted, rules, book, chapter, applyToTitle = true)
    }

    fun content(
        content: CharSequence?,
        rules: List<ReplacementRule>,
        book: Book?,
        chapter: Chapter?,
        jianFanMode: Int,
        fullWidthConvert: Boolean,
    ): String {
        val converted = convert(content?.toString().orEmpty(), jianFanMode, fullWidthConvert)
        if (converted.isEmpty()) return converted
        val indented = converted.lines().joinToString("\n") { "$PARAGRAPH_INDENT$it" }
        return applyRules(indented, rules, book, chapter, applyToTitle = false)
    }

    /**
     * 全角规范化 + 简繁转换
     */
    fun convert(text: String, jianFanMode: Int, fullWidthConvert: Boolean): String {
        val normalized = if (fullWidthConvert) normalizeText(text) else text
        return when (jianFanMode) {
            JIAN_TO_FAN -> ChineseUtils.s2t(normalized)
            FAN_TO_JIAN -> ChineseUtils.t2s(normalized)
            else -> normalized
        }
    }

    private fun applyRules(
        text: String,
        rules: List<ReplacementRule>,
        book: Book?,
        chapter: Chapter?,
        applyToTitle: Boolean,
    ): String {
        if (rules.isEmpty()) return text
        return ReplacementRuleUseCase.applyRules(text, rules, book, chapter, applyToTitle)
    }

    /**
     * 字符规范化，分三趟遍历字符数组完成：
     * 1. 所有空格统一转为全角空格；其余半角字符（字母/数字/标点）转为全角；繁体引号转简体。
     * 2. 全角英文字母、数字转回半角。
     * 3. 前后均为英文/数字的全角标点转为半角标点（如 A，B -> A,B）。
     */
    fun normalizeText(text: String): String {
        if (text.isEmpty()) return text
        val chars = text.toCharArray()
        // 第一趟：半角字符全部转全角。半角空格->全角空格，其余半角可打印字符+0xFEE0
        for (i in chars.indices) {
            val code = chars[i].code
            chars[i] = when {
                // 所有空格字符（半角空格、NBSP、各种宽度空格、全角空格等）统一转为全角空格
                Character.isSpaceChar(chars[i]) -> '\u3000'
                // 半角可打印字符（标点/数字/字母） -> 全角
                code in 0x21..0x7E -> (code + 0xFEE0).toChar()
                // 繁体引号「」『』等转为简体""''，其余原样保留
                else -> toSimplifiedPunct(chars[i])
            }
        }
        // 第二趟：全角英文字母/数字转回半角
        for (i in chars.indices) {
            val code = chars[i].code
            if (code in 0xFF10..0xFF19 || code in 0xFF21..0xFF3A || code in 0xFF41..0xFF5A) {
                chars[i] = (code - 0xFEE0).toChar()
            }
        }
        // 第三趟：前后均为英文/数字的全角标点转为半角标点（如 A，B -> A,B）
        for (i in chars.indices) {
            val code = chars[i].code
            // 仅当左右紧邻字符均为 ASCII 字母/数字时，才把全角标点转为半角英文标点
            if (code in 0xFF01..0xFF5E
                && i > 0 && isAsciiAlnum(chars[i - 1])
                && i < chars.size - 1 && isAsciiAlnum(chars[i + 1])
            ) {
                chars[i] = (code - 0xFEE0).toChar()
            }
        }
        return String(chars)
    }

    /**
     * 判断字符是否为 ASCII 字母或数字（半角）。
     */
    fun isAsciiAlnum(c: Char): Boolean {
        return c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '.'
    }

    /**
     * 繁体标点转简体：将繁体直角引号「」『』（含竖排小号变体）转为简体弯引号""''。
     */
    private fun toSimplifiedPunct(c: Char): Char = when (c) {
        '\u300C', '\uFE41' -> '\u201C'
        '\u300D', '\uFE42' -> '\u201D'
        '\u300E', '\uFE43' -> '\u2018'
        '\u300F', '\uFE44' -> '\u2019'
        else -> c
    }
}
