package com.sjianjun.reader.async

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

class AsyncLoadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    val loadView: ContentLoadingProgressBar = ContentLoadingProgressBar(context, attrs)

    private var stateList: SparseArray<Parcelable>? = null
    private var isRestoreState = false

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

    fun setContentView(it: View,dispatchState: Boolean) {
        addView(it, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val stateList = stateList ?: return
        if (isRestoreState && dispatchState) {
            isRestoreState = false
            super.dispatchRestoreInstanceState(stateList)
        }
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>?) {
        super.dispatchRestoreInstanceState(container)
        stateList = container
        isRestoreState = true
    }
}