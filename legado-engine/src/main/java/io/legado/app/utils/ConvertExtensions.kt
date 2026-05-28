@file:Suppress("unused")

package io.legado.app.utils

import android.content.res.Resources


fun Int.dpToPx(): Int = this.toFloat().dpToPx().toInt()

fun Float.dpToPx(): Float = android.util.TypedValue.applyDimension(
    android.util.TypedValue.COMPLEX_UNIT_DIP, this, Resources.getSystem().displayMetrics
)