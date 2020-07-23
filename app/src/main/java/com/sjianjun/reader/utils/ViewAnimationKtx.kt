package com.sjianjun.reader.utils

import android.view.View
import android.view.animation.*
import androidx.annotation.AnimRes
import androidx.annotation.UiThread
import com.sjianjun.reader.App

private val interpolator = AccelerateInterpolator()

@UiThread
fun View.animFadeIn(time: Long = 500) {
    show()
    if (time <= 0) {
        return
    }
    alpha = 0f
    animate()
        .alpha(1f)
        .setDuration(time)
        .setInterpolator(interpolator)
        .start()
    android.R.anim.fade_in
}

@UiThread
fun View.animFadeOut(time: Long = 500) {
    alpha = 1f
    if (time <= 0) {
        hide()
        return
    }
    animate()
        .alpha(0f)
        .setDuration(time)
        .setInterpolator(interpolator)
        .withEndAction {
            hide()
        }.start()
}

fun View.animWith(@AnimRes res: Int, fillAfter: Boolean = true) {
    animWith(AnimationUtils.loadAnimation(App.app, res), fillAfter)
}

fun View.animWith(animation: Animation, fillAfter: Boolean = true) {
    animation.fillAfter = fillAfter
    startAnimation(animation)
}