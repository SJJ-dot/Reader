package com.sjianjun.reader.view

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.constant.RefreshState
import com.scwang.smart.refresh.layout.constant.SpinnerStyle
import com.scwang.smart.refresh.layout.simple.SimpleComponent
import com.sjianjun.reader.R

/**
 * 文字 + 转圈动画的下拉/上拉组件基类。
 *
 * 头尾通用的状态：下拉 [RefreshState.PullDownToRefresh] / 上拉 [RefreshState.PullUpToLoad]，
 * 松手 [RefreshState.RefreshReleased] / [RefreshState.LoadReleased]，
 * 执行中 [RefreshState.Refreshing] / [RefreshState.Loading]。
 */
abstract class RefreshTextComponent @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SimpleComponent(context, attrs, 0) {

    private val spinner = RotateLoading(context).apply {
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
            marginEnd = dp(6)
        }
    }

    private val title = TextView(context).apply {
        maxLines = 1
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }

    private val hint = TextView(context).apply {
        maxLines = 1
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        visibility = View.GONE
        //撑满宽度 + 文字居中，避免副提示跑到左边
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private val titleRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(spinner)
        addView(title)
    }

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(titleRow)
        addView(hint)
    }

    init {
        mSpinnerStyle = SpinnerStyle.Translate
        addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        setAccentColor(R.color.colorPrimary)
        showText(pullingText(), hintText())
    }

    /** 未达到触发比例时的提示 */
    protected open fun pullingText(): String = "下拉检查更新"

    /** 达到触发比例、松手即执行时的提示 */
    protected open fun releaseText(): String = "松手检查更新"

    /** 执行中的提示 */
    protected open fun loadingText(): String = "正在检查更新…"

    /** 结束时的提示 */
    protected open fun finishText(success: Boolean): String =
        if (success) "检查完成" else "检查失败"

    /** 副提示，用于说明"再拉一点会做什么"，返回 null 则不显示 */
    protected open fun hintText(): String? = null

    /** 主色：文字、转圈、副提示颜色统一跟随 */
    protected fun setAccentColor(colorRes: Int) {
        val color = context.getColor(colorRes)
        title.setTextColor(color)
        hint.setTextColor(ColorUtils.setAlphaComponent(color, 0x99))
        spinner.setLoadingColor(color)
    }

    /** 主标题是否加粗，用于强调更"重"的一级 */
    protected fun setTitleBold(bold: Boolean) {
        title.setTypeface(title.typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    override fun onMoving(
        isDragging: Boolean, percent: Float, offset: Int, height: Int, maxDragHeight: Int
    ) {
        super.onMoving(isDragging, percent, offset, height, maxDragHeight)
        if (!isDragging) return
        showText(if (percent >= 1f) releaseText() else pullingText(), hintText())
    }

    override fun onStateChanged(
        refreshLayout: RefreshLayout, oldState: RefreshState, newState: RefreshState
    ) {
        super.onStateChanged(refreshLayout, oldState, newState)
        when (newState) {
            RefreshState.Refreshing, RefreshState.RefreshReleased,
            RefreshState.Loading, RefreshState.LoadReleased -> {
                showText(loadingText(), hintText())
                showSpinner()
            }

            RefreshState.None, RefreshState.PullDownToRefresh, RefreshState.PullUpToLoad -> {
                showText(pullingText(), hintText())
                hideSpinner()
            }

            else -> Unit
        }
    }

    override fun onFinish(refreshLayout: RefreshLayout, success: Boolean): Int {
        showText(finishText(success), hintText())
        hideSpinner()
        return super.onFinish(refreshLayout, success)
    }

    private fun showText(titleText: String, hintText: String?) {
        title.text = titleText
        if (hintText.isNullOrBlank()) {
            hint.visibility = View.GONE
        } else {
            hint.text = hintText
            hint.visibility = View.VISIBLE
        }
    }

    protected fun showSpinner() {
        spinner.visibility = View.VISIBLE
        spinner.isLoading = true
    }

    protected fun hideSpinner() {
        spinner.isLoading = false
        spinner.visibility = View.GONE
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
