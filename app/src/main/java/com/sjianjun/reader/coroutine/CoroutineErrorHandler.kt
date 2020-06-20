/*
 * Created by shen jian jun on 2020-06-18
 */
package com.sjianjun.reader.coroutine

import com.sjianjun.reader.BuildConfig
import com.sjianjun.reader.utils.toast
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import sjj.alog.Log

val coroutineErrorHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    Log.e("Coroutine Error:${throwable.message}; coroutineContext:$coroutineContext",throwable)
    if (BuildConfig.DEBUG) {
        GlobalScope.launch {
            toast("error:${throwable.message}")
        }
    }
}