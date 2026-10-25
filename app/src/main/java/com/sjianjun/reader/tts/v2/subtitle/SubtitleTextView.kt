package com.sjianjun.reader.tts.v2.subtitle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * 字幕文字控件：固定显示 [lineCount] 行，内部按宽度折行整段文本并自动滚动到当前行。
 *
 * 为什么要自绘而不用 TextView：
 * 1. TextView 的 justificationMode 在部分 ROM（含中文）不生效，右侧会留白 → 这里逐字均分剩余宽度实现两端对齐；
 * 2. TextView 放在固定高度的父容器里时会被 AT_MOST 约束，只能布局前 N 行，导致读进度推进后后面的文字出不来
 *    → 这里自己按整段生成 StaticLayout，绘制窗口内的行，滚动完全可控。
 */
class SubtitleTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private companion object {
        /** 行间距倍数（与原来 TextView 的 lineSpacingMultiplier 一致） */
        const val LINE_SPACING = 1.2f

        /** 已读进度的高亮底色（深墨绿，压在白字下方：不刺眼、与未读区分明显） */
        const val HIGHLIGHT_BG_COLOR = 0xCC2E6B34.toInt()

        /** 高亮块相对文字的上下留白 */
        const val HIGHLIGHT_PADDING_V = 2f
    }

    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(2f, 0f, 1f, 0x99000000.toInt())
    }

    /** 已读进度的高亮背景（只铺底色，文字本身仍是白色） */
    private val highlightBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HIGHLIGHT_BG_COLOR
        style = Paint.Style.FILL
    }

    private var text: String = ""
    private var layout: StaticLayout? = null

    private var fontSizeSp: Int = 17
    private var lineCount: Int = 2
    private var charIndex: Int = 0

    /** 单行高度（含行间距） */
    private var lineHeight: Float = 0f

    /** 设置字号与显示行数（变化时重建折行与重新测量） */
    fun setStyle(fontSizeSp: Int, lineCount: Int) {
        val sizeChanged = this.fontSizeSp != fontSizeSp
        val linesChanged = this.lineCount != lineCount
        if (!sizeChanged && !linesChanged) return
        this.fontSizeSp = fontSizeSp
        this.lineCount = lineCount
        if (sizeChanged) {
            val textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, fontSizeSp.toFloat(), resources.displayMetrics
            )
            paint.textSize = textSizePx
            lineHeight = (paint.fontMetrics.descent - paint.fontMetrics.ascent) * LINE_SPACING
            layout = null
        }
        rebuildLayout()
        requestLayout()
        invalidate()
    }

    /** 设置段落文本（相同则忽略，避免每 100ms 重建折行） */
    fun setContent(newText: String) {
        if (newText == text) return
        text = newText
        rebuildLayout()
        invalidate()
    }

    /** 当前读到第几个字符：用于计算窗口起始行，以及「开头到此处」的进度高亮 */
    fun setCharIndex(index: Int) {
        if (index == charIndex) return
        charIndex = index
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) {
            layout = null
            rebuildLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (lineHeight <= 0f) initMetrics()
        setMeasuredDimension(width, (lineHeight * lineCount).toInt().coerceAtLeast(1))
    }

    private fun initMetrics() {
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, fontSizeSp.toFloat(), resources.displayMetrics
        )
        paint.textSize = textSizePx
        lineHeight = (paint.fontMetrics.descent - paint.fontMetrics.ascent) * LINE_SPACING
    }

    private fun rebuildLayout() {
        val width = width
        if (width <= 0 || text.isEmpty()) {
            layout = null
            return
        }
        if (lineHeight <= 0f) initMetrics()
        layout = runCatching {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, LINE_SPACING)
                .setIncludePad(false)
                .build()
        }.getOrNull()
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout ?: return
        if (l.lineCount <= 0 || lineHeight <= 0f) return
        val safeChar = charIndex.coerceIn(0, (text.length - 1).coerceAtLeast(0))
        val currentLine = l.getLineForOffset(safeChar)
        // 以当前行为中心上下展开
        val lead = (lineCount - 1) / 2
        val maxStart = (l.lineCount - lineCount).coerceAtLeast(0)
        val startLine = (currentLine - lead).coerceIn(0, maxStart)

        for (i in 0 until lineCount) {
            val line = startLine + i
            if (line >= l.lineCount) break
            val top = i * lineHeight
            val baseline = top - paint.fontMetrics.ascent
            drawJustifiedLine(canvas, l, line, baseline)
        }
    }

    /**
     * 绘制单行：
     * - 除段落最后一行外，逐字均分剩余宽度实现两端对齐（中文没有词间空格，只能按字符分配间隙）；
     * - 从段落开头到当前进度（[charIndex]）的字符铺一层半透明高亮底色，文字保持白色。
     */
    private fun drawJustifiedLine(canvas: Canvas, l: StaticLayout, line: Int, baseline: Float) {
        val start = l.getLineStart(line)
        val rawEnd = l.getLineEnd(line)
        val end = if (rawEnd > start && text[rawEnd - 1] == '\n') rawEnd - 1 else rawEnd
        if (end <= start) return

        val isParagraphEnd = line == l.lineCount - 1
        val extra = if (isParagraphEnd || end - start <= 1) {
            0f
        } else {
            (width - paint.measureText(text.substring(start, end))).coerceAtLeast(0f)
        }
        val gapCount = (end - start - 1).coerceAtLeast(1)
        val gap = if (extra > 1f) extra / gapCount else 0f

        val bgTop = baseline + paint.fontMetrics.ascent - HIGHLIGHT_PADDING_V
        val bgBottom = baseline + paint.fontMetrics.descent + HIGHLIGHT_PADDING_V
        var x = 0f
        for (idx in start until end) {
            val ch = text[idx].toString()
            val charWidth = paint.measureText(ch)
            // 已读进度：从段落开头到当前字符统一铺一层背景，文字本身保持白色
            // 背景宽度带上字间隙，保证已读区域连成一条不间断的色带
            if (idx <= charIndex) {
                canvas.drawRect(x, bgTop, x + charWidth + gap, bgBottom, highlightBgPaint)
            }
            canvas.drawText(ch, x, baseline, paint)
            x += charWidth + gap
        }
    }
}
