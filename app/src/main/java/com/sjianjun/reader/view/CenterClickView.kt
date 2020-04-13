package com.sjianjun.reader.view

import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.hypot

class CenterClickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    var touchable = true
    var centerClickListener: View.OnClickListener? = null

    private val slop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private val center = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        center.set(0F, 0F, w.toFloat(), h.toFloat())

    }

    private var touchX = 0f
    private var touchY = 0f
    private var centerClickable = false
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                touchY = event.y
                centerClickable = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (centerClickable &&
                    hypot((event.x - touchX).toDouble(), (event.y - touchY).toDouble()) > slop
                ) {
                    centerClickable = false
                }
            }
            MotionEvent.ACTION_UP -> {
                if (centerClickable && center.contains(event.x, event.y)) {
                    centerClickListener?.onClick(this)
                    event.action = MotionEvent.ACTION_CANCEL
                    if (touchable) {
                        super.onTouchEvent(event)
                    }
                    return true
                }
            }
        }

        return if (touchable) {
            super.onTouchEvent(event)
        } else {
            true
        }
    }

}