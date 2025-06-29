package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sjianjun.reader.utils.dp2Px

class MaxHeightSwipeRefreshLayout(context: Context, attrs: AttributeSet?) : SwipeRefreshLayout(context, attrs) {


    private val reservedHeight: Int = 450.dp2Px
    private val minHeight: Int = 90.dp2Px

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val screenHeight = context.resources.displayMetrics.heightPixels // 获取屏幕高度
        val adjustedMaxHeight = maxOf(screenHeight - reservedHeight, minHeight)
        val maxHeightSpec = MeasureSpec.makeMeasureSpec(adjustedMaxHeight, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, maxHeightSpec)
    }
}