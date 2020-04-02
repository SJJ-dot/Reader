package com.sjianjun.reader.utils

import kotlinx.coroutines.*
import sjj.alog.Log
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

val handler = CoroutineExceptionHandler { _, exception ->
    Log.e("Caught $exception", exception)
}

fun launch(
    context: CoroutineContext = EmptyCoroutineContext + handler,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) {
    GlobalScope.launch(context, start, block)
}

fun launchMain(
    context: CoroutineContext = Dispatchers.Main + handler,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) {
    launch(context, start, block)
}