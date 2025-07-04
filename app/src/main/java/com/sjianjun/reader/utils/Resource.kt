package com.sjianjun.reader.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.sjianjun.reader.App
import kotlin.math.roundToInt

/*
 * Created by shen jian jun on 2020-07-14
 */
fun dpToPx(dp: Float): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        App.app.resources.displayMetrics
    )
}

inline val Int.dp2Px: Int
    get() = dpToPx(this.toFloat()).roundToInt()

fun Int.color(context: Context? = App.app): Int {
    val ctx = context ?: App.app
    return ContextCompat.getColor(ctx, this)
}

val String.color
    get() = Color.parseColor(this)


val Int.drawable: Drawable
    get() = ResourcesCompat.getDrawable(App.app.resources, this, App.app.theme)
        ?: ColorDrawable(Color.TRANSPARENT)