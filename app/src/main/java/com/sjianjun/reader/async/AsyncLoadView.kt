package com.sjianjun.reader.async

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout

class AsyncLoadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    val loadView: ContentLoadingProgressBar = ContentLoadingProgressBar(context, attrs)

    init {
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.CENTER
        loadView.layoutParams = params
        addView(loadView)
    }

    fun hide() {
        loadView.hide()
    }

    fun show() {
        loadView.show()
    }
}