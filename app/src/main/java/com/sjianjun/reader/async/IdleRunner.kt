package com.sjianjun.reader.async

import android.os.Handler
import android.os.Looper

private val idleHandler by lazy { Handler(Looper.getMainLooper()) }

fun runOnIdle(runner: () -> Unit) {
    idleHandler.post {
        Looper.myQueue().addIdleHandler {
            runner()
            false
        }
    }
}