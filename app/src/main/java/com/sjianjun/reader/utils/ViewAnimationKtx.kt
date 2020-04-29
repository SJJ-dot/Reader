package com.sjianjun.reader.utils

import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.AnimRes
import androidx.annotation.UiThread
import com.sjianjun.reader.App

@UiThread
fun View.animFadeIn() {
    show()
    alpha = 0f
    animate().alpha(1f).setDuration(500).start()
}

@UiThread
fun View.animFadeOut() {
    alpha = 1f
    animate().alpha(0f).setDuration(500).withEndAction {
        gone()
    }.start()
}

fun View.animWith(@AnimRes res: Int, fillAfter: Boolean = true) {
    animWith(AnimationUtils.loadAnimation(App.app, res), fillAfter)
}

fun View.animWith(animation: Animation, fillAfter: Boolean = true) {
    animation.fillAfter = fillAfter
    startAnimation(animation)
}