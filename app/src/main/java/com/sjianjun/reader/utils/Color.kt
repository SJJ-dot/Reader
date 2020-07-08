package com.sjianjun.reader.utils

import android.graphics.Color

/*
 * Created by shen jian jun on 2020-07-08
 */
val String.color
    get() = Color.parseColor(this)