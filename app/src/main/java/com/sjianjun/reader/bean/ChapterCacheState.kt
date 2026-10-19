package com.sjianjun.reader.bean

import androidx.annotation.ColorRes
import com.sjianjun.reader.R

/**
 * 章节目录的缓存状态。
 *
 * 判定依据是 `ChapterContent` 表里的实际内容（**不是** `Chapter.isLoaded`）：
 * - 有可用内容、没有失败记录 → [CACHED]
 * - 有可用内容、但还有失败的分页/失败标记 → [PARTIAL]（部分缓存，重试后会补齐）
 * - 只有失败内容（`contentError = 1`）→ [FAILED]
 * - 没有内容 → [NONE]
 *
 * 之前用 `isLoaded` 判定，而抓取失败也会写一条内容行并把 `isLoaded` 置 true，
 * 于是"缓存失败"的章节在目录里也显示成已缓存。
 */
object ChapterCacheState {

    /** 未缓存（灰色） */
    const val NONE = 0

    /** 已缓存（绿色） */
    const val CACHED = 1

    /** 缓存失败（红色） */
    const val FAILED = 2

    /** 部分缓存：有可用内容，但后续某页失败/尚未补全（黄色） */
    const val PARTIAL = 3

    /** 按"可用分页数 / 失败分页数"判定状态 */
    fun of(validCount: Int, errorCount: Int): Int = when {
        validCount > 0 && errorCount > 0 -> PARTIAL
        validCount > 0 -> CACHED
        errorCount > 0 -> FAILED
        else -> NONE
    }

    /** 目录标记颜色：未缓存灰、已缓存绿、失败红、部分缓存黄 */
    @ColorRes
    fun markColor(state: Int): Int = when (state) {
        CACHED -> R.color.mdr_green_A700
        FAILED -> R.color.mdr_red_600
        PARTIAL -> R.color.mdr_yellow_600
        else -> R.color.mdr_grey_500
    }
}
