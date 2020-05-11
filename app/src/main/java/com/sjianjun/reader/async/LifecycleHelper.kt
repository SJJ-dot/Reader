package com.sjianjun.reader.async

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import sjj.alog.Logger
import java.util.concurrent.atomic.AtomicBoolean

class LifecycleHelper(
    private val logger: Logger?,
    private val onInit: () -> Unit,
    private val onStart: () -> Unit,
    private val onResume: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit
) : LifecycleObserver {
    private val initState = AtomicBoolean(true)
    private val initializer = {
        if (initState.compareAndSet(true, false)) {
            onInit()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun start() {
        logger?.e("pid:${android.os.Process.myPid()} ${this}")
        initializer()
        onStart.invoke()
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resume() {
        logger?.e("pid:${android.os.Process.myPid()} ${this}")
        initializer()
        onResume.invoke()
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun pause() {
        logger?.e("pid:${android.os.Process.myPid()} ${this}")
        onPause.invoke()
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stop() {
        logger?.e("pid:${android.os.Process.myPid()} ${this}")
        onStop.invoke()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun destroy() {
        initState.lazySet(true)
    }

}