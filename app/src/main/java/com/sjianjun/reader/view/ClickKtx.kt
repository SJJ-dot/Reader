package com.sjianjun.reader.view

import android.view.View


fun View.click(interval: Long = 500, block: (View) -> Unit) {
    var lastClickTime = 0L
    setOnClickListener {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > interval) {
            lastClickTime = currentTime
            block(this)
        }
    }
}