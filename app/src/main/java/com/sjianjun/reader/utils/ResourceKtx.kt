package com.sjianjun.reader.utils

import com.sjianjun.reader.App

fun Int.resColor(): Int {
    return App.app.resources.getColor(this)
}