package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewConfiguration
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import kotlin.math.abs

/*
 * Created by shen jian jun on 2020-07-10
 */
class HideBottomViewOnScrollBehavior2<V : View> : HideBottomViewOnScrollBehavior<V> {
    constructor()
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        return super.onLayoutChild(parent, child, layoutDirection)
    }

    var dySum = 0

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
        val dy = if (dyConsumed == 0) dyUnconsumed else dyConsumed
        dySum += abs(dy)

        super.onNestedScroll(
            coordinatorLayout,
            child,
            target,
            dxConsumed,
            if (abs(dySum) > ViewConfiguration.get(coordinatorLayout.context).scaledTouchSlop) dy else 0,
            dxUnconsumed,
            dyUnconsumed,
            type,
            consumed
        )
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        type: Int
    ) {
        super.onStopNestedScroll(coordinatorLayout, child, target, type)
        dySum = 0
    }
}