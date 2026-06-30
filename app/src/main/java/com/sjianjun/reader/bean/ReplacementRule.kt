package com.sjianjun.reader.bean

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 替换规则.
 */
@Entity
class ReplacementRule {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()

    /**
     * 规则名称
     */
    var name: String = ""

    /**
     * 规则。普通字符串或者正则表达式
     */
    var rule: String = ""
    var replacement: String = ""
    var isRegex: Boolean = false

    /**
     * 启用开关位掩码：
     * - [ENABLE_READER]（1）= 普通阅读器（BookReaderActivity）启用
     * - [ENABLE_READ_MODE]（2）= 阅读模式（BrowserReadModeActivity）启用
     * - 两者都启用 = 3
     */
    var enabledMask: Int = 0

    fun isEnableForFlag(flag: Int): Boolean {
        return enabledMask and flag != 0
    }

    fun setEnableForFlag(flag: Int, enable: Boolean) {
        enabledMask = if (enable) {
            enabledMask or flag
        } else {
            enabledMask and flag.inv()
        }
    }

    /**
     * 规则优先级，数值越小优先级越高
     */
    var order: Int = 0

    /**
     * 作用范围：是否应用于章节标题
     */
    var applyToTitle: Boolean = false

    /**
     * 作用范围：是否应用于章节内容
     */
    var applyToContent: Boolean = true

    /**
     * 替换范围：书名或者书籍URL包含该字符串才进行替换
     */
    var scope: String? = null

    /**
     * 排除范围：书名或者书籍URL包含该字符串就不进行替换
     */
    var excludeScope: String? = null

    override fun toString(): String {
        return "ReplacementRule(name='$name', rule='$rule', replacement='$replacement', isRegex=$isRegex, enabledMask=$enabledMask, order=$order, applyToTitle=$applyToTitle, applyToContent=$applyToContent, scope=$scope, excludeScope=$excludeScope)"
    }

    companion object {
        /** 普通阅读器（BookReaderActivity）启用位 */
        const val ENABLE_READER: Int = 1

        /** 阅读模式（BrowserReadModeActivity）启用位 */
        const val ENABLE_READ_MODE: Int = 2
    }
}
