package com.sjianjun.reader.coroutine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext

/*
 * Created by shen jian jun on 2020-07-14
 */

public suspend fun <T> withMain(
    context: CoroutineContext = Dispatchers.Main,
    block: suspend CoroutineScope.() -> T
): T {
    return withContext(context, block)
}

public suspend fun <T> withDefault(
    context: CoroutineContext = Dispatchers.Default,
    block: suspend CoroutineScope.() -> T
): T {
    return withContext(context, block)
}

public suspend fun <T> withIo(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend CoroutineScope.() -> T
): T {
    return withContext(context, block)
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

public fun launchGlobal(
    context: CoroutineContext = coroutineErrorHandler,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return GlobalScope.launch(context, start, block)
}