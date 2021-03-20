package com.sjianjun.async.view

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

class AsyncInflateContainerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var stateList: SparseArray<Parcelable>? = null
    private var isRestoreState = false
    private var isEnableDispatchState = false

    fun setContentView(it: View, dispatchState: Boolean) {
        addView(it, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        this.isEnableDispatchState = dispatchState
        dispatchChildRestoreInstanceState()
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>?) {
        super.dispatchThawSelfOnly(container)
        stateList = container
        isRestoreState = true
        dispatchChildRestoreInstanceState()
    }

    private fun dispatchChildRestoreInstanceState() {
        val stateList = stateList ?: return
        if (isRestoreState && isEnableDispatchState) {
            isRestoreState = false
            super.dispatchRestoreInstanceState(stateList)
        }
    }
}