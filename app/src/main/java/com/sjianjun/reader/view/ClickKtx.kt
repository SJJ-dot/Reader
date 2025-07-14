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

fun View.clickWithDouble(interval: Long = 300, onClick: (View) -> Unit = {}, onDoubleClick: (View) -> Unit = {}) {
    var lastClickTime = 0L
    var clickCount = 0
    setOnClickListener {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < interval) {
            clickCount++
        } else {
            clickCount = 1
        }
        lastClickTime = currentTime
        if (clickCount == 1) {
            postDelayed({
                if (clickCount == 1) {
                    onClick(this)
                }
            }, interval)
        } else if (clickCount == 2) {
            onDoubleClick(this)
        }

    }
}