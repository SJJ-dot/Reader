package com.sjianjun.reader.utils

import android.widget.TextView

fun TextView.setTextColorRes(colorRes:Int) {
    setTextColor(resources.getColor(colorRes))
}