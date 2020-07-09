package com.sjianjun.reader.utils

import com.donkingliang.consecutivescroller.ConsecutiveScrollerLayout

/*
 * Created by shen jian jun on 2020-07-09
 */
val ConsecutiveScrollerLayout.canScrollVertically: Boolean
    get() = !isScrollTop || !isScrollBottom