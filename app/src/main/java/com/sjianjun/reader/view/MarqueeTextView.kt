package com.sjianjun.reader.view

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {


    init {
        marqueeRepeatLimit = -1
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.MARQUEE
        isHorizontalFadingEdgeEnabled = true
        isFocusable = true
        isFocusableInTouchMode = true
        setHorizontallyScrolling(true)
    }

    override fun isFocused(): Boolean {
        return true
    }

}