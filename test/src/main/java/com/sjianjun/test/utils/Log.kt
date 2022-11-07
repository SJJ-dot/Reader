package com.sjianjun.test.utils

object Log {

    @JvmStatic
    fun e(s: Any?) {
        println(s)
    }

    @JvmStatic
    fun e(s: Any?, t: Throwable?) {
        println(s)
        println(t?.stackTraceToString() ?: return)
    }

    fun i(it: String?) {
        println(it)
    }
}