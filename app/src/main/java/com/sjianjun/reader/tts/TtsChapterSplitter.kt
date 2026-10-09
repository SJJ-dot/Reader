package com.sjianjun.reader.tts

import sjj.novel.view.reader.page.TxtLine
import sjj.novel.view.reader.page.TxtPage

/**
 * 段落内定位结果
 *
 * @param pagePos 目标行所在页号（用于自动翻页）
 * @param line 目标行
 * @param indexInParagraph 目标行在该段落内的序号（0 开始），用于判断高亮是否需要更新
 */
data class LocatedLine(
    val pagePos: Int,
    val line: TxtLine,
    val indexInParagraph: Int,
)

/**
 * 章节正文分段工具，与阅读页排版逻辑解耦。
 *
 * 阅读页排版时（`PageLoader`）的规则是「一行原文 = 一段」：
 * 遍历 `text.lines()` 跳过空白行，每段换行折行后只在最后一行打 `isParaEnd` 标记。
 * 因此这里用同样的规则切分纯文本，保证「段落序号」在服务端与阅读页两侧语义一致：
 * 服务端按序号朗读并回传段内进度，阅读页按「段落序号 + 段内进度」定位到具体行做高亮。
 *
 * 注意：标题作为第一段（阅读页把标题单独排版在正文最前面），与旧朗读行为一致。
 */
object TtsChapterSplitter {

    /**
     * 把章节正文切成段落列表
     */
    fun split(chapterTitle: String?, content: CharSequence?): List<String> {
        val paragraphs = ArrayList<String>()
        // 标题与正文都按「一行原文 = 一段」处理，与阅读页「先排标题再排正文」一致
        appendParagraphs(paragraphs, chapterTitle)
        appendParagraphs(paragraphs, content?.toString())
        return paragraphs
    }

    private fun appendParagraphs(paragraphs: MutableList<String>, text: String?) {
        if (text.isNullOrEmpty()) return
        for (line in text.lines()) {
            // 朗读不需要行首缩进（阅读页排版时的 　　 缩进只是排版效果）
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                paragraphs.add(trimmed)
            }
        }
    }

    /**
     * 朗读权重：按字符与标点估算朗读耗时（标点停顿更久）。
     * 服务端据此估算段落时长与进度，阅读页据此把段内进度折算到具体行，两侧共用同一模型。
     */
    fun speakingWeight(text: CharSequence?): Long {
        if (text.isNullOrEmpty()) return 1L
        var weight = 0f
        for (char in text) {
            weight += when (char) {
                ',', '，', '、' -> 2f
                '.', '。', '!', '！', '?', '？', ';', '；' -> 2.2f
                ' ', '\t', '\n', '　', '\u3000' -> 0.6f
                else -> 1.0f
            }
        }
        return weight.toLong().coerceAtLeast(1L)
    }

    /**
     * 阅读页当前所在页对应的段落序号（从本段开始朗读，而不是每次都从章首开始）。
     * 段落可能跨页，所以按「全文行序」统计目标页首行所属的段落。
     */
    fun paragraphIndexAtPage(pages: List<TxtPage>?, pagePos: Int): Int {
        if (pages.isNullOrEmpty()) return 0
        val target = pagePos.coerceIn(0, pages.size - 1)
        var index = -1
        var inParagraph = false
        for ((pageIndex, page) in pages.withIndex()) {
            for ((lineIndex, line) in page.lines.withIndex()) {
                if (pageIndex == target && lineIndex == 0) {
                    // 目标页首行仍是上一段的续行时属于当前段，否则它就是新一段的开头
                    return (if (inParagraph) index else index + 1).coerceAtLeast(0)
                }
                if (!inParagraph) {
                    index++
                    inParagraph = true
                }
                if (line.isParaEnd) {
                    inParagraph = false
                }
            }
        }
        return index.coerceAtLeast(0)
    }

    /**
     * 按段落序号 + 段内进度定位到具体行（阅读器据此「按行高亮」）。
     * 段落跨页时行会分布到多页，返回的是目标行真正所在的页。
     *
     * @param progress 段内已朗读进度 0f~1f，由服务端估算回传
     */
    fun locateLineInParagraph(pages: List<TxtPage>?, paragraphIndex: Int, progress: Float): LocatedLine? {
        if (pages.isNullOrEmpty() || paragraphIndex < 0) return null
        var index = -1
        var inParagraph = false
        var collecting = false
        val collected = ArrayList<LocatedLine>()
        for (page in pages) {
            for (line in page.lines) {
                if (!inParagraph) {
                    // 上一段结束：目标段落已收集完
                    if (collecting) return pickLocatedLine(collected, progress)
                    index++
                    collecting = index == paragraphIndex
                    inParagraph = true
                }
                if (collecting) {
                    collected.add(LocatedLine(page.position, line, collected.size))
                }
                if (line.isParaEnd) {
                    inParagraph = false
                }
            }
        }
        return if (collecting) pickLocatedLine(collected, progress) else null
    }

    /**
     * 在阅读页已排版好的页面里定位段落起始行（按 `isParaEnd` 分组，段落跨页也能正确分组）。
     *
     * @return 起始行所在页号（用于自动翻页）与该行；定位不到返回 null
     */
    fun locateParagraph(pages: List<TxtPage>?, paragraphIndex: Int): Pair<Int, TxtLine>? {
        val located = locateLineInParagraph(pages, paragraphIndex, 0f) ?: return null
        return located.pagePos to located.line
    }

    /**
     * 兜底定位：段落序号对不上时（例如正文被替换规则改写过导致行数变化），
     * 用段落文本在排版结果里找起始行。找不到就放弃高亮，不影响朗读。
     */
    fun locateByParagraphText(pages: List<TxtPage>?, paragraphText: String?): Pair<Int, TxtLine>? {
        if (pages.isNullOrEmpty() || paragraphText.isNullOrBlank()) return null
        val target = paragraphText.trim()
        if (target.length < MIN_MATCH_LENGTH) return null
        for (page in pages) {
            for (line in page.lines) {
                val lineText = line.txt.trim().trimStart('　')
                if (lineText.length >= MIN_MATCH_LENGTH && target.startsWith(lineText)) {
                    return page.position to line
                }
            }
        }
        return null
    }

    /**
     * 按段内进度挑出当前行：以每行自身的朗读权重分配进度区间，进度落在哪一行就高亮哪一行
     */
    private fun pickLocatedLine(lines: List<LocatedLine>, progress: Float): LocatedLine? {
        if (lines.isEmpty()) return null
        if (lines.size == 1) return lines.first()
        val weights = LongArray(lines.size)
        var total = 0L
        for (i in lines.indices) {
            weights[i] = speakingWeight(lines[i].line.txt)
            total += weights[i]
        }
        if (total <= 0L) return lines.first()
        val target = (total * progress.coerceIn(0f, 1f)).toLong()
        var accumulated = 0L
        for (i in weights.indices) {
            accumulated += weights[i]
            if (target < accumulated) return lines[i]
        }
        return lines.last()
    }

    /** 兜底匹配的最小长度，太短容易误匹配 */
    private const val MIN_MATCH_LENGTH = 6
}
