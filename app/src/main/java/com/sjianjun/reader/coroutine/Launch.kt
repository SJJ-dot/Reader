/*
 * Created by shen jian jun on 2020-06-18
 */
package com.sjianjun.reader.coroutine

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

private val singleCoroutineMap =
    ConcurrentHashMap<CoroutineScope, Callable<ConcurrentHashMap<String, Callable<Job>>>>()

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

        val callable = singleCoroutineMap.getOrPut(coroutineScope, {
            val callable = object : Callable<ConcurrentHashMap<String, Callable<Job>>> {
                override fun call(): ConcurrentHashMap<String, Callable<Job>> {
                    coroutineScope.coroutineContext[Job]?.invokeOnCompletion {
                        singleCoroutineMap.remove(coroutineScope, this)
                    }
                    return ConcurrentHashMap()
                }
            }
            callable
        })
        val map = callable.call()
        val jobCallable = map.getOrPut(singleCoroutineKey) {
            val jobCallable = object : Callable<Job> {
                override fun call(): Job {
                    val job = coroutineScope.launch(context + coroutineErrorHandler, start, block)
                    job.invokeOnCompletion {
                        map.remove(singleCoroutineKey, this)
                    }
                    return job
                }
            }
            jobCallable
        }
        jobCallable.call()
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