package com.sjianjun.reader.utils

import android.util.TypedValue
import com.sjianjun.reader.App
import kotlin.math.roundToInt

fun dpToPx(dp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,dp,App.app.resources.displayMetrics)
}

fun Int.dpToPx(): Int {
    return dpToPx(this.toFloat()).roundToInt()
}