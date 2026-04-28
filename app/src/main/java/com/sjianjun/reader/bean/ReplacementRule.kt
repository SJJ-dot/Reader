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

    var isEnabled: Boolean = true

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
         return "ReplacementRule(name='$name', rule='$rule', replacement='$replacement', isRegex=$isRegex, isEnabled=$isEnabled, order=$order, applyToTitle=$applyToTitle, applyToContent=$applyToContent, scope=$scope, excludeScope=$excludeScope)"
     }
}