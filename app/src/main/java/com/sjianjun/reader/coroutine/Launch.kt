/*
 * Created by shen jian jun on 2020-06-18
 */
package com.sjianjun.reader.coroutine

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

private val singleCoroutineMap =
    ConcurrentHashMap<CoroutineScope, Lazy<ConcurrentHashMap<String, Job>>>()

fun Fragment.launchIo(
    context: CoroutineContext = Dispatchers.IO,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    singleCoroutineKey: String = "",
    block: suspend CoroutineScope.() -> Unit
): Job {
    return viewLifecycleOwner.lifecycle.launch(context, start, singleCoroutineKey, block)
}

fun Fragment.launch(
    context: CoroutineContext = Dispatchers.Main,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    singleCoroutineKey: String = "",
    block: suspend CoroutineScope.() -> Unit
): Job {
    return viewLifecycleOwner.lifecycle.launch(context, start, singleCoroutineKey, block)
}

fun FragmentActivity.launchIo(
    context: CoroutineContext = Dispatchers.IO,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    singleCoroutineKey: String = "",
    block: suspend CoroutineScope.() -> Unit
): Job {
    return lifecycle.launch(context, start, singleCoroutineKey, block)
}

fun FragmentActivity.launch(
    context: CoroutineContext = Dispatchers.Main,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    singleCoroutineKey: String = "",
    block: suspend CoroutineScope.() -> Unit
): Job {
    return lifecycle.launch(context, start, singleCoroutineKey, block)
}


fun Lifecycle.launch(
    context: CoroutineContext = Dispatchers.Main,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    singleCoroutineKey: String = "",
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
    context: CoroutineContext = Dispatchers.IO,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    singleCoroutineKey: String = "",
    block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(context, start, singleCoroutineKey, block)
}

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