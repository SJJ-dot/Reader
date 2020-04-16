package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import kotlin.math.abs

class HorizontalScrollView2 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {
    private var touchable = true

    private val slop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    private var touchY = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        when (ev?.action) {
            MotionEvent.ACTION_DOWN -> {
                touchable = true
                touchY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchable &&
                    abs(ev.y - touchY) > slop
                ) {
                    touchable = false
                }
            }
        }
        return if (!touchable) {
            false
        } else {
            super.onInterceptTouchEvent(ev)
        }
    }
}