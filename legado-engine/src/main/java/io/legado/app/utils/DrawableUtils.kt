@file:Suppress("unused")

package io.legado.app.utils

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat


fun Drawable.setTintMutate(
    @ColorInt tint: Int,
    tintMode: PorterDuff.Mode = PorterDuff.Mode.SRC_ATOP
) {
    val wrappedDrawable = DrawableCompat.wrap(this)
    wrappedDrawable.mutate()
    DrawableCompat.setTintMode(wrappedDrawable, tintMode)
    DrawableCompat.setTint(wrappedDrawable, tint)
}