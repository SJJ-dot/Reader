package com.sjianjun.reader.coroutine

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/*
 * Created by shen jian jun on 2020-07-14
 */

private val singleCoroutineMap =
    ConcurrentHashMap<CoroutineScope, Lazy<ConcurrentHashMap<String, Job>>>()

fun Lifecycle.launch(
    singleCoroutineKey: String = "",
    context: CoroutineContext = Dispatchers.Main,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return if (singleCoroutineKey.isEmpty()) {
        coroutineScope.launch(context + coroutineErrorHandler, start, block)
    } else {

        val mapLazy = singleCoroutineMap.getOrPut(coroutineScope, {
            var lazy: Lazy<ConcurrentHashMap<String, Job>>? = null
            lazy = lazy {
                coroutineScope.coroutineContext[Job]?.invokeOnCompletion {
                    singleCoroutineMap.remove(coroutineScope, lazy)
                }
                ConcurrentHashMap<String, Job>()
            }
            lazy
        })

        val map = mapLazy.value
        val job = coroutineScope.launch(context + coroutineErrorHandler, start, block)

        map.put(singleCoroutineKey, job)?.cancel()

        job.invokeOnCompletion {
            map.remove(singleCoroutineKey, job)
        }

        return job
    }
}

fun Lifecycle.launchIo(
    singleCoroutineKey: String = "",
    context: CoroutineContext = Dispatchers.IO,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(singleCoroutineKey, context, start, block)
}
