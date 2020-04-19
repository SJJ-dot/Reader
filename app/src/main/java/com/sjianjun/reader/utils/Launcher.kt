package com.sjianjun.reader.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import sjj.alog.Log
import kotlin.coroutines.CoroutineContext

val handler = CoroutineExceptionHandler { _, exception ->
    Log.e("Caught $exception", exception)
}

public fun launchGlobal(
    context: CoroutineContext = handler,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return GlobalScope.launch(context, start, block)
}

suspend inline fun <T> withIo(noinline block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.IO) {
        block()
    }
}

suspend inline fun <T> withDefault(noinline block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.Default) {
        block()
    }
}

suspend inline fun <T> withMain(noinline block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.Main) {
        block()
    }
}

fun <T> Flow<T>.flowIo(): Flow<T> {
    return flowOn(Dispatchers.IO)
}

fun <T> Flow<T>.flowDefault(): Flow<T> {
    return flowOn(Dispatchers.Default)
}

fun <T> Flow<T>.flowMain(): Flow<T> {
    return flowOn(Dispatchers.Main)
}