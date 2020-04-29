package com.sjianjun.reader.utils

import com.sjianjun.reader.App

fun Int.getColor(): Int {
    return App.app.resources.getColor(this)
}