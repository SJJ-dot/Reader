package com.sjianjun.reader.tts.v2.subtitle

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.SeekBar
import com.sjianjun.reader.databinding.LayoutSubtitleWindowBinding
import com.sjianjun.reader.preferences.globalConfig
import com.sjianjun.reader.tts.v2.session.TtsSessionState
import sjj.alog.Log
import kotlin.math.abs

/**
 * 朗读字幕（悬浮字幕）。
 *
 * 交互：
 * - 整条铺满屏幕宽度，可上下拖动移动位置（位置会记住）；
 * - 右下角设置图标 → 展开/收起设置面板（字号 / 透明度 / 行数 / 偏移），改动实时生效；
 * - 右上角关闭图标 → 关闭字幕（并同步通知栏开关状态）。
 *
 * 显示策略：只取**当前段落**文本折行，以"当前读到的行"为中心上下展开 N 行（不跨段落）。
 * 生命周期由朗读通知服务持有：会话开始默认关闭，由通知按钮手动开启。
 */
class TtsSubtitleWindow(private val context: Context) {

    /** 用户点右上角关闭时回调（服务层据此隐藏并刷新通知栏状态） */
    var onCloseRequest: (() -> Unit)? = null

    private companion object {
        const val TAG = "TtsSubtitle"

        const val FONT_MIN = 12
        const val ALPHA_MIN = 20
        const val ALPHA_MAX = 100
        const val OFFSET_STEPS = 30
        const val OFFSET_STEP_MS = 100
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var binding: LayoutSubtitleWindowBinding? = null
    private var params: WindowManager.LayoutParams? = null

    /** 当前 TextView 里的段落文本（变化时才重新设置并等布局） */
    private var lastText: String = ""

    /** 最近一次渲染的会话状态：设置项变更时用它立即重绘 */
    private var lastState: TtsSessionState? = null

    private var dragging = false
    private var downRawY = 0f
    private var startY = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** 关闭/设置按钮自动隐藏（设置面板展开时保持常显） */
    private val hideButtonsRunnable = Runnable { hideActionButtonsIfAllowed() }

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
            WindowManager.LayoutParams.MATCH_PARENT,
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
        lp.x = 0
        val restored = applySavedPosition(lp)

        val added = runCatching { wm.addView(view.root, lp) }.isSuccess
        if (!added) {
            Log.e("$TAG 添加悬浮字幕窗口失败")
            return false
        }
        binding = view
        params = lp
        lastText = ""

        view.subtitleSettings.visibility = View.GONE
        hideActionButtons()

        view.subtitleClose.setOnClickListener {
            val callback = onCloseRequest
            if (callback != null) callback() else hide()
        }
        view.subtitleSettingsBtn.setOnClickListener { toggleSettings() }

        setupDrag(view)
        setupSettings(view)
        applyStyle()
        if (restored) {
            // 尺寸变化（旋转/不同分辨率）后把恢复的位置收进屏幕内
            view.root.post { clampIntoBounds() }
        }
        return true
    }

    fun hide() {
        val view = binding ?: return
        view.root.removeCallbacks(hideButtonsRunnable)
        runCatching { wm.removeView(view.root) }
        binding = null
        params = null
        lastText = ""
        lastState = null
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

    /** 上下拖动移动字幕（宽度铺满，水平方向不需要移动） */
    private fun setupDrag(view: LayoutSubtitleWindowBinding) {
        view.subtitlePanel.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    downRawY = event.rawY
                    startY = lp.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downRawY
                    if (!dragging && abs(dy) > touchSlop) dragging = true
                    if (dragging) moveTo(startY + dy)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // 拖动=移动位置；单击=呼出关闭/设置按钮（3 秒后自动隐藏）
                    if (dragging) savePosition() else showActionButtons()
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

    private fun moveTo(y: Float) {
        val view = binding ?: return
        val lp = params ?: return
        val screenH = screenHeightPx()
        lp.y = y.toInt().coerceIn(0, (screenH - view.root.height).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(view.root, lp) }
    }

    /**
     * 显示右上角关闭、右下角设置按钮。
     * 设置面板未展开时 3 秒后自动隐藏；展开期间保持常显。
     */
    private fun showActionButtons() {
        val view = binding ?: return
        view.subtitleClose.visibility = View.VISIBLE
        view.subtitleSettingsBtn.visibility = View.VISIBLE
        view.root.removeCallbacks(hideButtonsRunnable)
        if (!isSettingsOpen()) {
            view.root.postDelayed(hideButtonsRunnable, 3000L)
        }
    }

    private fun hideActionButtons() {
        val view = binding ?: return
        view.root.removeCallbacks(hideButtonsRunnable)
        view.subtitleClose.visibility = View.GONE
        view.subtitleSettingsBtn.visibility = View.GONE
    }

    /** 倒计时到点：设置面板还开着就保持显示，否则隐藏 */
    private fun hideActionButtonsIfAllowed() {
        if (isSettingsOpen()) return
        hideActionButtons()
    }

    private fun isSettingsOpen(): Boolean =
        binding?.subtitleSettings?.visibility == View.VISIBLE

    /** 展开/收起设置面板：展开期间关闭/设置按钮常显，收起后重新计时 3 秒 */
    private fun toggleSettings() {
        val view = binding ?: return
        val show = view.subtitleSettings.visibility != View.VISIBLE
        view.subtitleSettings.visibility = if (show) View.VISIBLE else View.GONE
        showActionButtons()
        if (show) clampInsideScreen()
    }

    /** 展开设置后若超出屏幕，向上收紧 */
    private fun clampInsideScreen() {
        val view = binding ?: return
        val lp = params ?: return
        view.root.post {
            val screenH = screenHeightPx()
            val overflow = lp.y + view.root.height - screenH
            if (overflow > 0) {
                lp.y = (lp.y - overflow).coerceAtLeast(0)
                runCatching { wm.updateViewLayout(view.root, lp) }
            }
        }
    }

    /** 读取上次保存的纵向位置（屏幕高度千分比 → 像素） */
    private fun applySavedPosition(lp: WindowManager.LayoutParams): Boolean {
        val screenH = screenHeightPx()
        val ry = globalConfig.ttsSubtitlePosYRatio
        val restored = ry in 0..1000
        lp.y = if (restored) {
            (screenH * ry / 1000f).toInt()
        } else {
            (screenH * 0.72f).toInt()
        }
        return restored
    }

    /** 拖动结束后记录纵向位置（存千分比，适应旋转与不同分辨率） */
    private fun savePosition() {
        val lp = params ?: return
        val screenH = screenHeightPx()
        if (screenH <= 0) return
        globalConfig.ttsSubtitlePosYRatio = (lp.y * 1000f / screenH).toInt().coerceIn(0, 1000)
        Log.i("$TAG 保存字幕位置 y=${lp.y}")
    }

    /** 把窗口限制在屏幕可见范围内 */
    private fun clampIntoBounds() {
        val view = binding ?: return
        val lp = params ?: return
        val screenH = screenHeightPx()
        val ny = lp.y.coerceIn(0, (screenH - view.root.height).coerceAtLeast(0))
        if (ny != lp.y) {
            lp.y = ny
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
        applyStyle()
        lastState?.let { renderText(it) }
    }

    // ==================== 渲染 ====================

    private fun applyStyle() {
        val view = binding ?: return
        val fontSize = (globalConfig.ttsSubtitleFontSize.value ?: 17).coerceIn(FONT_MIN, 28)
        val alpha = (globalConfig.ttsSubtitleAlpha.value ?: 85).coerceIn(ALPHA_MIN, ALPHA_MAX) / 100f

        view.subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
        view.subtitleText.setShadowLayer(2f, 0f, 1f, 0x99000000.toInt())
        // 先用字号估算窗口高度（真实行高在布局完成后校准）
        val estimate = (TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat(), context.resources.displayMetrics
        ) * 1.2f).toInt()
        updateWindowHeight(estimate)
        // 透明度只作用于字幕文字区，设置面板保持不透明便于操作
        view.subtitlePanel.alpha = alpha
    }

    private fun renderText(state: TtsSessionState) {
        val view = binding ?: return
        val text = state.paragraphText
        if (text.isBlank()) return
        val tv = view.subtitleText
        if (lastText != text) {
            lastText = text
            tv.text = text
            // 文本变了要等 TextView 重新布局后才能算行高/滚动位置
            tv.post { syncScroll(state) }
            return
        }
        syncScroll(state)
    }

    /**
     * 把整段文本滚动到窗口内的目标行（两端对齐由 TextView 的 justificationMode 完成）。
     * 窗口固定 N 行高，靠 translationY 上移显示窗口内的那几行。
     */
    private fun syncScroll(state: TtsSessionState) {
        val view = binding ?: return
        val tv = view.subtitleText
        val layout = tv.layout ?: return
        val text = tv.text?.toString().orEmpty()
        if (text.isBlank() || layout.lineCount <= 0) return
        val lines = configuredLines()
        val lineHeight = (layout.getLineBottom(0) - layout.getLineTop(0)).coerceAtLeast(1)
        updateWindowHeight(lineHeight)

        val charIndex = currentCharIndex(state, text)
        val currentLine = layout.getLineForOffset(charIndex)
        val lead = (lines - 1) / 2
        val maxStart = (layout.lineCount - lines).coerceAtLeast(0)
        val startLine = (currentLine - lead).coerceIn(0, maxStart)
        tv.translationY = -layout.getLineTop(startLine).toFloat()
    }

    /** 字幕窗口固定显示 [configuredLines] 行 */
    private fun updateWindowHeight(lineHeight: Int) {
        val view = binding ?: return
        val target = lineHeight * configuredLines()
        val lp = view.subtitleLines.layoutParams
        if (lp.height != target) {
            lp.height = target
            view.subtitleLines.layoutParams = lp
        }
    }

    private fun configuredLines(): Int =
        (globalConfig.ttsSubtitleLines.value ?: 2).coerceIn(1, 4)

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

    /**
     * 真实屏幕尺寸。
     * Service 里拿到的 displayMetrics 有时不是当前屏幕（例如返回较小的默认配置），
     * 会导致折行宽度偏窄、右侧留出大片空白，所以统一从 WindowManager 取。
     */
    private fun screenBounds(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.currentWindowMetrics.bounds
        bounds.width() to bounds.height()
    } else {
        val dm = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        dm.widthPixels to dm.heightPixels
    }

    private fun screenHeightPx(): Int = screenBounds().second

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
