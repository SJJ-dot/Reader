package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import com.scwang.smart.refresh.layout.api.RefreshHeader
import com.sjianjun.reader.R

/**
 * 详情页两级下拉头部：
 * - 一级（percent < [level2Rate]）：检查更新，主题绿、不加粗
 * - 二级（percent >= [level2Rate]）：重新加载（全量更新），强调色 + 加粗
 *
 * 两级除了文字不同，还通过颜色/字重和副提示区分，避免分不清当前是哪一级。
 *
 * 这里没有使用库的 TwoLevelHeader：它会被测成整页高度（SpinnerStyle.MatchLayout），
 * 二楼动画会把内部头部的 translationY 置为页面高度，导致页面打开时下拉箭头停在页面底部。
 */
class TwoLevelRefreshHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RefreshTextComponent(context, attrs), RefreshHeader {

    /**
     * 二级（重新加载）触发比例：1.0 是一级"松手检查更新"的点。
     *
     * 间距换算：percent 每 1.0 = HeaderTriggerRate(0.7) × 头部高度(56dp) ≈ 39dp，
     * 所以这里的 1.64 ≈ 一级之后再拉 25dp 进入重新加载
     */
    var level2Rate = 1.8f

    /**
     * 是否处于二级下拉
     */
    var isLevel2 = false
        private set

    override fun onMoving(
        isDragging: Boolean, percent: Float, offset: Int, height: Int, maxDragHeight: Int
    ) {
        if (isDragging) {
            val level2 = percent >= level2Rate
            if (level2 != isLevel2) {
                isLevel2 = level2
                //二级用强调色 + 加粗，和一级一眼就能区分
                setAccentColor(if (level2) R.color.dn_badge_color else R.color.colorPrimary)
                setTitleBold(level2)
            }
        }
        super.onMoving(isDragging, percent, offset, height, maxDragHeight)
    }

    override fun releaseText(): String = if (isLevel2) "松手重新加载" else "松手检查更新"

    override fun loadingText(): String = if (isLevel2) "正在重新加载…" else "正在检查更新…"

    override fun finishText(success: Boolean): String = when {
        isLevel2 && success -> "重新加载完成"
        isLevel2 -> "重新加载失败"
        success -> "检查完成"
        else -> "检查失败"
    }

    override fun hintText(): String = if (isLevel2) "全量更新章节列表" else "继续下拉：重新加载"
}
