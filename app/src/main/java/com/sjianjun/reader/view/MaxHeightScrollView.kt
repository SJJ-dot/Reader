package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.widget.NestedScrollView
import com.sjianjun.reader.R

class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    private var maxHeightPx: Int = 0
    private var minHeightPx: Int = 0

    init {
        context.obtainStyledAttributes(attrs, R.styleable.MaxHeightScrollView, defStyleAttr, 0).apply {
            maxHeightPx = getDimensionPixelSize(R.styleable.MaxHeightScrollView_maxHeight, 0)
            minHeightPx = getDimensionPixelSize(R.styleable.MaxHeightScrollView_minHeight, 0)
            recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (maxHeightPx <= 0 && minHeightPx <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        var minBound = if (minHeightPx > 0) minHeightPx else 0
        var maxBound = if (maxHeightPx > 0) maxHeightPx else Int.MAX_VALUE
        if (minBound > maxBound) minBound = maxBound
        if (mode == MeasureSpec.EXACTLY) {
            if (maxBound < height || maxBound == Int.MAX_VALUE) {
                maxBound = height
            }
            if (minBound > height || minBound == 0) {
                minBound = height
            }
        }
        val heightSpec = MeasureSpec.makeMeasureSpec(maxBound, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, heightSpec)
        if (measuredHeight < minBound) {
            setMeasuredDimension(measuredWidth, minBound)
        } else if (measuredHeight > maxBound) {
            setMeasuredDimension(measuredWidth, maxBound)
        }
    }
}