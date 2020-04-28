package com.sjianjun.reader.utils

import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.AnimRes
import com.sjianjun.reader.App

fun View.animFadeIn() {
    animWith(android.R.anim.fade_in)
}

fun View.animFadeOut() {
    animWith(android.R.anim.fade_out)
}

fun View.animWith(@AnimRes res: Int, fillAfter: Boolean = true) {
    animWith(AnimationUtils.loadAnimation(App.app, res), fillAfter)
}

fun View.animWith(animation: Animation, fillAfter: Boolean = true) {
    animation.fillAfter = fillAfter
    startAnimation(animation)
}