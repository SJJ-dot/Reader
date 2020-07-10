package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior

/*
 * Created by shen jian jun on 2020-07-10
 */
class HideBottomViewOnScrollBehavior2<V : View> : HideBottomViewOnScrollBehavior<V> {
    constructor()
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        return super.onLayoutChild(parent, child, layoutDirection)
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {

        super.onNestedScroll(
            coordinatorLayout,
            child,
            target,
            dxConsumed,
            if (dyConsumed == 0) dyUnconsumed else dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            type,
            consumed
        )
    }
}