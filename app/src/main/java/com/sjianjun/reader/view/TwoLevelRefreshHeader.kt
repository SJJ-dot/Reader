package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.scwang.smart.refresh.layout.api.RefreshHeader
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.constant.RefreshState
import com.scwang.smart.refresh.layout.constant.SpinnerStyle
import com.scwang.smart.refresh.layout.simple.SimpleComponent
import com.sjianjun.reader.R

/**
 * 两级下拉头部：
 * - 一级（percent < [level2Rate]）：检查更新
 * - 二级（percent >= [level2Rate]）：重新加载（全量更新）
 *
 * 这里没有使用库的 TwoLevelHeader：它会被测成整页高度（SpinnerStyle.MatchLayout），
 * 二楼动画会把内部头部的 translationY 置为页面高度，导致页面打开时下拉箭头停在页面底部，
 * 需要手动滑动一次才复位。
 */
class TwoLevelRefreshHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SimpleComponent(context, attrs, 0), RefreshHeader {

    /**
     * 二级（重新加载）触发比例，相对头部高度
     */
    var level2Rate = 1.4f

    /**
     * 是否处于二级下拉
     */
    var isLevel2 = false
        private set

    private val spinner = RotateLoading(context).apply {
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
            marginEnd = dp(6)
        }
    }

    private val title = TextView(context).apply {
        maxLines = 1
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(context.getColor(R.color.colorPrimary))
        text = TEXT_PULLING
    }

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(spinner)
        addView(title)
    }

    init {
        mSpinnerStyle = SpinnerStyle.Translate
        addView(
            content,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        )
    }

    override fun onMoving(
        isDragging: Boolean, percent: Float, offset: Int, height: Int, maxDragHeight: Int
    ) {
        super.onMoving(isDragging, percent, offset, height, maxDragHeight)
        if (!isDragging) return
        isLevel2 = percent >= level2Rate
        title.text = when {
            isLevel2 -> TEXT_RELEASE_LEVEL2
            percent >= 1f -> TEXT_RELEASE
            else -> TEXT_PULLING
        }
    }

    override fun onStateChanged(
        refreshLayout: RefreshLayout, oldState: RefreshState, newState: RefreshState
    ) {
        super.onStateChanged(refreshLayout, oldState, newState)
        when (newState) {
            RefreshState.Refreshing, RefreshState.RefreshReleased -> {
                title.text = if (isLevel2) TEXT_REFRESHING_LEVEL2 else TEXT_REFRESHING
                showSpinner()
            }

            RefreshState.None, RefreshState.PullDownToRefresh -> {
                title.text = TEXT_PULLING
                hideSpinner()
            }

            else -> Unit
        }
    }

    override fun onFinish(refreshLayout: RefreshLayout, success: Boolean): Int {
        title.text = if (success) TEXT_FINISH else TEXT_FAILED
        hideSpinner()
        return super.onFinish(refreshLayout, success)
    }

    private fun showSpinner() {
        spinner.visibility = View.VISIBLE
        spinner.isLoading = true
    }

    private fun hideSpinner() {
        spinner.isLoading = false
        spinner.visibility = View.GONE
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val TEXT_PULLING = "下拉检查更新"
        private const val TEXT_RELEASE = "松手检查更新"
        private const val TEXT_RELEASE_LEVEL2 = "松手重新加载"
        private const val TEXT_REFRESHING = "正在检查更新…"
        private const val TEXT_REFRESHING_LEVEL2 = "正在重新加载…"
        private const val TEXT_FINISH = "检查完成"
        private const val TEXT_FAILED = "检查失败"
    }
}
