package com.sjianjun.reader.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar
import kotlin.math.roundToInt

class VerticalSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.seekBarStyle,
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    private var listener: OnSeekBarChangeListener? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.rotate(-90f)
        canvas.translate(-height.toFloat(), 0f)
        super.onDraw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }

    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        onSizeChanged(width, height, 0, 0)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP -> {
                val trackHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1)
                val touchY = (event.y - paddingTop).coerceIn(0f, trackHeight.toFloat())
                val scale = 1f - (touchY / trackHeight.toFloat())
                progress = (scale * max.toFloat()).roundToInt().coerceIn(0, max)
                onSizeChanged(width, height, 0, 0)
                if (event.action == MotionEvent.ACTION_UP) {
                    performClick()
                }
                listener?.onProgressChanged(this, progress, true)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    fun setOnSeekBarChangeListener(listener: OnSeekBarChangeListener) {
        this.listener = listener
    }

    fun interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: VerticalSeekBar?, progress: Int, fromUser: Boolean)
    }
}

