package com.sjianjun.reader.utils

import java.text.SimpleDateFormat
import java.util.*

fun simpleDateFormat(pattern: String): SimpleDateFormat {
    return SimpleDateFormat(pattern, Locale.getDefault())
}