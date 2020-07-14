package com.sjianjun.reader.coroutine

import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

/*
 * Created by shen jian jun on 2020-07-14
 */
fun Fragment.launchIo(
    singleCoroutineKey: String = "",
    context: CoroutineContext = Dispatchers.IO,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return viewLifecycleOwner.lifecycle.launch(singleCoroutineKey, context, start, block)
}

fun Fragment.launch(
    singleCoroutineKey: String = "",
    context: CoroutineContext = Dispatchers.Main,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return viewLifecycleOwner.lifecycle.launch(singleCoroutineKey, context, start, block)
}