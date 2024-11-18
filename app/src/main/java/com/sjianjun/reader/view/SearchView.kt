package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import com.sjianjun.reader.utils.dp2Px

class SearchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : androidx.appcompat.widget.SearchView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        48dp
        if (isIconified) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } else {
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val width = MeasureSpec.getSize(widthMeasureSpec)
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width -48.dp2Px, widthMode),
                heightMeasureSpec
            )
        }
    }
}