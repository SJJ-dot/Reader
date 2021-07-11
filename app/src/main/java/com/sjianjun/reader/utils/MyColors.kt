package com.sjianjun.reader.utils

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate

object MyColors {
    @ColorInt
    val NIGHT_FOREGROUND = Color.parseColor("#55000000")
    @ColorInt
    val NIGHT_BACKGROUND_1 = Color.parseColor("#121212")
}

val isNight: Boolean
    get() = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES