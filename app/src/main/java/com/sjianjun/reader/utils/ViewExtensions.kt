package com.sjianjun.reader.utils

import android.content.Context
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.appcompat.app.AppCompatActivity

private tailrec fun getCompatActivity(context: Context?): AppCompatActivity? {
    return when (context) {
        is AppCompatActivity -> context
        is androidx.appcompat.view.ContextThemeWrapper -> getCompatActivity(context.baseContext)
        is android.view.ContextThemeWrapper -> getCompatActivity(context.baseContext)
        else -> null
    }
}

val View.activity: AppCompatActivity?
    get() = getCompatActivity(context)

fun View.invisible() {
    if (visibility != INVISIBLE) {
        visibility = INVISIBLE
    }
}

fun View.visible() {
    if (visibility != VISIBLE) {
        visibility = VISIBLE
    }
}
