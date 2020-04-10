package com.sjianjun.reader

import sjj.alog.Log

fun handleDefaultException() {
    val handler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        Log.e(t, e)
//        handler?.uncaughtException(t, e)
    }
}