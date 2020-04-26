package com.sjianjun.reader.utils

import sjj.alog.Log

inline fun <T> tryBlock(runner: () -> T): T? {
    return try {
        runner()
    } catch (throwable: Throwable) {
        Log.i("catch error ", throwable)
        null
    }
}