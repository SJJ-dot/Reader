package com.sjianjun.reader.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

fun <T> Flow<T>.debounce(timeoutMillis: Int = 1000): Flow<T> {
    return debounce(timeoutMillis.toLong())
}