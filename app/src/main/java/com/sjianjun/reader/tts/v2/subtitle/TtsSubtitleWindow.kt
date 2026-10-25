package com.sjianjun.reader.tts.v2.subtitle

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.LayoutSubtitleWindowBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.tts.v2.session.TtsSessionState
import sjj.alog.Log
import kotlin.math.abs

/**
 * 朗读字幕（悬浮字幕）。
 *
 * 交互：
 * - 拖动字幕块可自由移动位置；
 * - 点击字幕块展开/收起下方的设置面板（字号 / 透明度 / 行数 / 偏移），改动实时生效；
 * - 右上角锁图标：锁定后点击字幕不再弹出设置，避免误触（仍可拖动）。
 *
 * 显示策略：只取**当前段落**文本折行，以"当前读到的行"为中心上下展开 N 行（不跨段落）。
 * 生命周期由朗读通知服务持有：会话开始默认关闭，由通知按钮手动开启。
 */
class TtsSubtitleWindow(private val context: Context) {

    private companion object {
        const val TAG = "TtsSubtitle"

        /** 字幕文字区宽度占屏幕宽度的比例 */
        const val WIDTH_RATIO = 0.86f

        const val FONT_MIN = 12
        const val ALPHA_MIN = 20
        const val ALPHA_MAX = 100
        const val OFFSET_STEPS = 30
        const val OFFSET_STEP_MS = 100
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var binding: LayoutSubtitleWindowBinding? = null
    private var params: WindowManager.LayoutParams? = null
    private val lineViews = mutableListOf<TextView>()

    /** 折行缓存（文本/宽度/字号不变时复用，避免每 100ms 重建 StaticLayout） */
    private var cachedLayout: StaticLayout? = null
    private var cachedKey: String = ""

    /** 最近一次渲染的会话状态：设置项变更时用它立即重绘 */
    private var lastState: TtsSessionState? = null

    /** 锁定后点击字幕不再弹出设置面板 */
    private var locked = false

    private var dragging = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    val isVisible: Boolean get() = binding != null

    /** 是否已获得悬浮窗权限 */
    fun canDrawOverlay(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** 显示字幕窗口（未授权/失败返回 false） */
    fun show(): Boolean {
        if (binding != null) return true
        if (!canDrawOverlay()) {
            Log.e("$TAG 未授予悬浮窗权限")
            return false
        }
        val view = LayoutSubtitleWindowBinding.inflate(LayoutInflater.from(context))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // 可触摸（拖动/点击），但不抢焦点，不影响输入法
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        // 恢复上次拖动保存的位置；没有记录时先放屏幕下方，稍后居中
        val restored = applySavedPosition(lp)
        val added = runCatching { wm.addView(view.root, lp) }.isSuccess
        if (!added) {
            Log.e("$TAG 添加悬浮字幕窗口失败")
            return false
        }
        binding = view
        params = lp
        locked = false
        cachedLayout = null
        cachedKey = ""
        lineViews.clear()

        // 文字区宽度固定，窗口宽度 wrap_content，这样左右拖动才有意义
        // 注意：subtitleLines 的父容器是 FrameLayout，必须用 FrameLayout.LayoutParams
        view.subtitleLines.layoutParams = FrameLayout.LayoutParams(
            textWidthPx(), FrameLayout.LayoutParams.WRAP_CONTENT
        )
        view.subtitleSettings.layoutParams = LinearLayout.LayoutParams(
            textWidthPx() + dp(40), LinearLayout.LayoutParams.WRAP_CONTENT
        )
        view.subtitleLock.setImageResource(R.drawable.ic_subtitle_unlock)
        view.subtitleSettings.visibility = View.GONE

        setupDragAndClick(view)
        setupSettings(view)
        applyStyle()
        if (restored) {
            // 尺寸变化（旋转/不同分辨率）后把恢复的位置收进屏幕内
            view.root.post { clampIntoBounds() }
        } else {
            centerHorizontally()
        }
        return true
    }

    fun hide() {
        val view = binding ?: return
        runCatching { wm.removeView(view.root) }
        binding = null
        params = null
        lineViews.clear()
        cachedLayout = null
        cachedKey = ""
        lastState = null
        locked = false
        dragging = false
    }

    /** 按当前会话状态刷新字幕文本 */
    fun render(state: TtsSessionState) {
        lastState = state
        if (binding == null) return
        applyStyle()
        renderText(state)
    }

    // ==================== 交互 ====================

    /** 拖动移动 + 点击展开设置 */
    private fun setupDragAndClick(view: LayoutSubtitleWindowBinding) {
        view.subtitleLock.setOnClickListener {
            locked = !locked
            view.subtitleLock.setImageResource(
                if (locked) R.drawable.ic_subtitle_lock else R.drawable.ic_subtitle_unlock
            )
            if (locked) view.subtitleSettings.visibility = View.GONE
        }
        view.subtitlePanel.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        moveTo(startX + dx, startY + dy)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) savePosition() else toggleSettings()
                    dragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }

                else -> true
            }
        }
    }

    private fun moveTo(x: Float, y: Float) {
        val view = binding ?: return
        val lp = params ?: return
        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        lp.x = x.toInt().coerceIn(0, (screenW - view.root.width).coerceAtLeast(0))
        lp.y = y.toInt().coerceIn(0, (screenH - view.root.height).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(view.root, lp) }
    }

    private fun toggleSettings() {
        val view = binding ?: return
        if (locked) return
        val show = view.subtitleSettings.visibility != View.VISIBLE
        view.subtitleSettings.visibility = if (show) View.VISIBLE else View.GONE
        if (show) clampInsideScreen()
    }

    /** 展开设置后若超出屏幕，向上收紧 */
    private fun clampInsideScreen() {
        val view = binding ?: return
        val lp = params ?: return
        view.root.post {
            val screenH = context.resources.displayMetrics.heightPixels
            val overflow = lp.y + view.root.height - screenH
            if (overflow > 0) {
                lp.y = (lp.y - overflow).coerceAtLeast(0)
                runCatching { wm.updateViewLayout(view.root, lp) }
            }
        }
    }

    /**
     * 读取上次保存的位置（屏幕千分比 → 像素）；没有记录时放在屏幕下方。
     * @return 是否成功恢复了保存的位置
     */
    private fun applySavedPosition(lp: WindowManager.LayoutParams): Boolean {
        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val rx = globalConfig.ttsSubtitlePosXRatio
        val ry = globalConfig.ttsSubtitlePosYRatio
        val restored = rx in 0..1000 && ry in 0..1000
        if (restored) {
            lp.x = (screenW * rx / 1000f).toInt()
            lp.y = (screenH * ry / 1000f).toInt()
        } else {
            lp.x = 0
            lp.y = (screenH * 0.72f).toInt()
        }
        return restored
    }

    /** 拖动结束后记录位置（存千分比，适应旋转与不同分辨率） */
    private fun savePosition() {
        val lp = params ?: return
        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        if (screenW <= 0 || screenH <= 0) return
        globalConfig.ttsSubtitlePosXRatio =
            (lp.x * 1000f / screenW).toInt().coerceIn(0, 1000)
        globalConfig.ttsSubtitlePosYRatio =
            (lp.y * 1000f / screenH).toInt().coerceIn(0, 1000)
        Log.i("$TAG 保存字幕位置 x=${lp.x} y=${lp.y}")
    }

    /** 把窗口限制在屏幕可见范围内 */
    private fun clampIntoBounds() {
        val view = binding ?: return
        val lp = params ?: return
        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val nx = lp.x.coerceIn(0, (screenW - view.root.width).coerceAtLeast(0))
        val ny = lp.y.coerceIn(0, (screenH - view.root.height).coerceAtLeast(0))
        if (nx != lp.x || ny != lp.y) {
            lp.x = nx
            lp.y = ny
            runCatching { wm.updateViewLayout(view.root, lp) }
        }
    }

    private fun centerHorizontally() {
        val view = binding ?: return
        val lp = params ?: return
        view.root.post {
            val screenW = context.resources.displayMetrics.widthPixels
            lp.x = ((screenW - view.root.width) / 2).coerceAtLeast(0)
            runCatching { wm.updateViewLayout(view.root, lp) }
        }
    }

    // ==================== 设置项 ====================

    private fun setupSettings(view: LayoutSubtitleWindowBinding) {
        // 字号
        view.sbSubtitleFont.progress =
            ((globalConfig.ttsSubtitleFontSize.value ?: 17) - FONT_MIN).coerceIn(0, 16)
        view.tvSubtitleFont.text = (view.sbSubtitleFont.progress + FONT_MIN).toString()
        view.sbSubtitleFont.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onChange(progress: Int) {
                val value = progress + FONT_MIN
                view.tvSubtitleFont.text = value.toString()
                globalConfig.ttsSubtitleFontSize.postValue(value)
                refreshNow()
            }
        })

        // 透明度
        view.sbSubtitleAlpha.progress =
            ((globalConfig.ttsSubtitleAlpha.value ?: 85) - ALPHA_MIN).coerceIn(0, ALPHA_MAX - ALPHA_MIN)
        view.tvSubtitleAlpha.text = "${view.sbSubtitleAlpha.progress + ALPHA_MIN}%"
        view.sbSubtitleAlpha.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onChange(progress: Int) {
                val value = progress + ALPHA_MIN
                view.tvSubtitleAlpha.text = "$value%"
                globalConfig.ttsSubtitleAlpha.postValue(value)
                refreshNow()
            }
        })

        // 行数
        view.sbSubtitleLines.progress =
            ((globalConfig.ttsSubtitleLines.value ?: 2) - 1).coerceIn(0, 3)
        view.tvSubtitleLinesValue.text = "${view.sbSubtitleLines.progress + 1} 行"
        view.sbSubtitleLines.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onChange(progress: Int) {
                val value = progress + 1
                view.tvSubtitleLinesValue.text = "$value 行"
                globalConfig.ttsSubtitleLines.postValue(value)
                refreshNow()
            }
        })

        // 文本偏移
        val savedOffset = globalConfig.ttsSubtitleOffsetMs.value ?: 0
        val savedProgress = (savedOffset / OFFSET_STEP_MS + OFFSET_STEPS / 2).coerceIn(0, OFFSET_STEPS)
        view.sbSubtitleOffset.progress = savedProgress
        view.tvSubtitleOffset.text = ((savedProgress - OFFSET_STEPS / 2) * OFFSET_STEP_MS).toString()
        view.sbSubtitleOffset.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onChange(progress: Int) {
                val value = (progress - OFFSET_STEPS / 2) * OFFSET_STEP_MS
                view.tvSubtitleOffset.text = value.toString()
                globalConfig.ttsSubtitleOffsetMs.postValue(value)
                refreshNow()
            }
        })
    }

    /** 设置变化后立即重绘（播放期间实时生效） */
    private fun refreshNow() {
        cachedLayout = null
        applyStyle()
        lastState?.let { renderText(it) }
    }

    // ==================== 渲染 ====================

    private fun applyStyle() {
        val view = binding ?: return
        val fontSize = (globalConfig.ttsSubtitleFontSize.value ?: 17).coerceIn(FONT_MIN, 28)
        val alpha = (globalConfig.ttsSubtitleAlpha.value ?: 85).coerceIn(ALPHA_MIN, ALPHA_MAX) / 100f
        val lines = (globalConfig.ttsSubtitleLines.value ?: 2).coerceIn(1, 4)

        if (lineViews.size != lines) {
            view.subtitleLines.removeAllViews()
            lineViews.clear()
            repeat(lines) {
                val tv = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        textWidthPx(), LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    includeFontPadding = false
                    maxLines = 1
                    setTextColor(Color.WHITE)
                    setShadowLayer(2f, 0f, 1f, 0x99000000.toInt())
                }
                view.subtitleLines.addView(tv)
                lineViews += tv
            }
            cachedLayout = null
            cachedKey = ""
        }
        lineViews.forEach {
            it.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
            it.alpha = 1f
        }
        // 透明度只作用于字幕块，设置面板保持不透明便于操作
        view.subtitlePanel.alpha = alpha
    }

    private fun renderText(state: TtsSessionState) {
        val text = state.paragraphText
        if (text.isBlank()) return
        val layout = obtainLayout(text) ?: return
        val lineCount = layout.lineCount
        if (lineCount <= 0) return

        val lines = lineViews.size
        val charIndex = currentCharIndex(state, text)
        val currentLine = layout.getLineForOffset(charIndex)
        // 以当前行为中心，向上/向下各展开
        val lead = (lines - 1) / 2
        val maxStart = (lineCount - lines).coerceAtLeast(0)
        val startLine = (currentLine - lead).coerceIn(0, maxStart)

        for (i in 0 until lines) {
            val tv = lineViews[i]
            val line = startLine + i
            if (line < lineCount) {
                val start = layout.getLineStart(line)
                val end = layout.getLineEnd(line)
                tv.text = text.substring(start, end).trimEnd('\n')
                tv.visibility = View.VISIBLE
            } else {
                tv.text = ""
                tv.visibility = View.INVISIBLE
            }
        }
    }

    /** 进度 → 字符下标（叠加时间偏移，用于修正文字超前/滞后） */
    private fun currentCharIndex(state: TtsSessionState, text: String): Int {
        val offsetMs = globalConfig.ttsSubtitleOffsetMs.value ?: 0
        val paragraphMs = if (state.paragraphCount > 0 && state.chapterDurationMs > 0) {
            state.chapterDurationMs / state.paragraphCount
        } else {
            0L
        }
        // 正偏移 = 文本提前（把进度往前推），负偏移 = 文本延后
        val offsetRatio = if (paragraphMs > 0) offsetMs.toFloat() / paragraphMs else 0f
        val progress = (state.paragraphProgress + offsetRatio).coerceIn(0f, 1f)
        if (text.isEmpty()) return 0
        return (progress * text.length).toInt().coerceIn(0, text.length - 1)
    }

    private fun obtainLayout(text: String): StaticLayout? {
        val fontSize = (globalConfig.ttsSubtitleFontSize.value ?: 17).coerceIn(FONT_MIN, 28)
        val width = textWidthPx()
        val key = "$text|$width|$fontSize"
        cachedLayout?.let { if (cachedKey == key) return it }
        val paint = TextPaint().apply {
            isAntiAlias = true
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat(), context.resources.displayMetrics
            )
        }
        val layout = runCatching {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build()
        }.onFailure { Log.e("$TAG 折行失败", it) }.getOrNull() ?: return null
        cachedLayout = layout
        cachedKey = key
        return layout
    }

    /** 字幕文字区宽度（固定值，便于窗口按内容收缩后自由拖动） */
    private fun textWidthPx(): Int {
        val screen = context.resources.displayMetrics.widthPixels
        return (screen * WIDTH_RATIO).toInt().coerceAtLeast(dp(160))
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()

    /** 只关心拖动进度的 SeekBar 监听（程序化赋值不触发保存） */
    private abstract class SimpleSeekListener : SeekBar.OnSeekBarChangeListener {
        abstract fun onChange(progress: Int)

        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }
}
