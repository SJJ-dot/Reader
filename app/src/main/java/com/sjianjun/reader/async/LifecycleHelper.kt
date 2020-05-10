package com.sjianjun.reader.async

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import sjj.alog.Logger
import java.util.concurrent.atomic.AtomicBoolean

class LifecycleHelper(
    private val logger: Logger?,
    private val onLoadedView: () -> Unit,
    private val onStart: () -> Unit,
    private val onResume: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit
) : LifecycleObserver {
    private val initState = AtomicBoolean(true)
    private val initializer = {
        if (initState.compareAndSet(true, false)) {
            onLoadedView()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        logger?.e("pid:${android.os.Process.myPid()} ${this}")
        initializer()
        this.onStart.invoke()
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        logger?.e("pid:${android.os.Process.myPid()} ${this}")
        initializer()
        this.onResume.invoke()
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        logger?.e("pid:${android.os.Process.myPid()} ${this}")
        this.onPause.invoke()
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        logger?.e("pid:${android.os.Process.myPid()} ${this}")
        this.onStop.invoke()
    }

}