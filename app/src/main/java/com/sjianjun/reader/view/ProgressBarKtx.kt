package com.sjianjun.reader.view

import androidx.core.widget.ContentLoadingProgressBar

fun ContentLoadingProgressBar.setLoading(show: Boolean) {
    if (show) {
        show()
    } else {
        hide()
    }
}