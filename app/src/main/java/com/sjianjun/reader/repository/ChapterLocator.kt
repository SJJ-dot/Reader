package com.sjianjun.reader.repository

import com.sjianjun.reader.bean.Chapter
import com.sjianjun.reader.repository.DbFactory.db
import com.sjianjun.reader.utils.normalizedChapterName
import kotlin.math.abs
import sjj.alog.Log

/**
 * 换源时的章节定位器。
 *
 * 匹配策略（逐级尝试，命中即返回）：
 * 1. 精确匹配：章节名完全一致（或去掉「第X章」前缀并归一化后一致），取序号最接近当前章节者；
 * 2. 锚点推断：取旧书源当前章节左侧最近的"精确同名"锚点，用其偏移量推算当前位置，并"向前贴近"
 *    （取序号不超过推算位置的最大章节），避免跳到当前尚未阅读的内容；
 * 3. 兜底：无左侧锚点时，直接按旧序号切换（向前贴近，不越界）。
 *
 * 性能：只在当前章节序号 ±[LOCAL_RADIUS] 的局部区间内查询新/旧书源章节，不一次性读取整本目录；
 * 当前序号超出新书源范围时收敛到最后一章。
 *
 * 注意：不采用章节名相似度匹配，避免因近似标题导致错误匹配。
 */
object ChapterLocator {

    /** 局部查询半径：以当前章节为中心，前后各查多少章 */
    private const val LOCAL_RADIUS = 50

    /**
     * 在当前阅读章节于新书源中定位对应章节。
     *
     * @param newBookId 新书源书籍 id
     * @param oldBookId 当前生效书源书籍 id（用于取锚点）
     * @param oldIndex  当前章节在旧书源中的序号
     * @param oldTitle  当前章节标题（可能为空）
     * @return 新书源中最接近的章节；新书源无目录时返回 null
     */
    suspend fun locateChapter(
        newBookId: String,
        oldBookId: String,
        oldIndex: Int,
        oldTitle: String?
    ): Chapter? {
        val chapterDao = db.chapterDao()
        val start = oldIndex - LOCAL_RADIUS
        val end = oldIndex + LOCAL_RADIUS
        val newChapters = chapterDao.getChapterListBetween(newBookId, start, end)
        if (newChapters.isEmpty()) {
            // 当前序号已超出新书源范围（例如新源章节更少），收敛到最后一章
            val last = chapterDao.getLastChapterByBookId(newBookId)
            Log.i("换源定位[越界] $oldTitle(index=$oldIndex) -> ${last?.title}(index=${last?.index})")
            return last
        }

        // 1. 精确匹配
        exactMatch(newChapters, oldIndex, oldTitle)?.let {
            Log.i("换源定位[精确] $oldTitle -> ${it.title}(index=${it.index})")
            return it
        }

        // 2. 锚点推断（左侧最近同名锚点 + 偏移）
        val oldChapters = chapterDao.getChapterListBetween(oldBookId, start, end)
        val guessed = inferIndex(oldChapters, newChapters, oldIndex)
        if (guessed != null) {
            val result = forwardNearest(newChapters, guessed)
            Log.i("换源定位[锚点] $oldTitle(index=$oldIndex) ~> $guessed -> ${result?.title}(index=${result?.index})")
            return result
        }

        // 3. 兜底：按旧序号切换（向前贴近，不越界）
        val result = forwardNearest(newChapters, oldIndex.toDouble())
        Log.i("换源定位[按序号] $oldTitle(index=$oldIndex) -> ${result?.title}(index=${result?.index})")
        return result
    }

    /** 第 1 级：章节名完全一致，取序号最接近当前章节者 */
    private fun exactMatch(newChapters: List<Chapter>, oldIndex: Int, oldTitle: String?): Chapter? {
        val title = oldTitle?.trim().orEmpty()
        if (title.isEmpty()) return null
        return newChapters
            .filter { titlesMatch(it.title, title) }
            .minByOrNull { abs(oldIndex - it.index) }
    }

    /** 章节名是否一致：原始一致，或去掉「第X章」前缀并归一化标点/全角后一致 */
    private fun titlesMatch(a: String?, b: String?): Boolean {
        val x = a?.trim().orEmpty()
        val y = b?.trim().orEmpty()
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val nx = x.normalizedChapterName()
        return nx.isNotEmpty() && nx == y.normalizedChapterName()
    }

    /**
     * 第 2 级：锚点推断（简化版）。
     * 取当前章节左侧最近的"精确同名"锚点，用其（新源序号 − 旧源序号）的偏移量推算当前位置；
     * 无左侧锚点时返回 null，交由调用方按旧序号兜底。
     */
    private fun inferIndex(oldChapters: List<Chapter>, newChapters: List<Chapter>, oldIndex: Int): Double? {
        // 新书源：归一化名 -> 章节，仅保留唯一命名，重名不参与锚点，避免歧义
        val uniqueByName = newChapters
            .groupBy { it.title.orEmpty().normalizedChapterName() }
            .filter { it.key.isNotEmpty() && it.value.size == 1 }
            .mapValues { it.value.first() }

        // 左侧最近的精确同名锚点
        val anchor = oldChapters
            .filter { it.index <= oldIndex }
            .mapNotNull { chapter ->
                val target = uniqueByName[chapter.title.orEmpty().normalizedChapterName()]
                if (target == null) null else chapter.index to target.index
            }
            .maxByOrNull { it.first }
            ?: return null

        val offset = anchor.second - anchor.first
        val newMin = newChapters.first().index
        val newMax = newChapters.last().index
        return (oldIndex + offset).toDouble().coerceIn(newMin.toDouble(), newMax.toDouble())
    }

    /** 向前贴近：取序号不超过 position 的最后一个章节；若 position 小于首章则取首章 */
    private fun forwardNearest(chapters: List<Chapter>, position: Double): Chapter? =
        chapters.lastOrNull { it.index <= position } ?: chapters.firstOrNull()
}
