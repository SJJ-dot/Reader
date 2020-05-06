package com.sjianjun.reader.async

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import sjj.alog.Config
import sjj.alog.Log
import sjj.alog.Logger
import java.util.concurrent.atomic.AtomicBoolean

private val logger  by lazy { Logger(Config().apply {
    consolePrintEnable = false
}) }

class LifecycleHelper(
                         private val onLoadedView: () -> Unit = {},
                         private val onCreate: () -> Unit = {},
                         private val onStart: () -> Unit = {},
                         private val onResume: () -> Unit = {},
                         private val onPause: () -> Unit = {},
                         private val onStop: () -> Unit = {},
                         private val onDestroy: () -> Unit = {}) : LifecycleObserver {

    private val createEnable = AtomicBoolean(true)
    private val startEnable = AtomicBoolean()
    private val resumeEnable = AtomicBoolean()
    private val pauseEnable = AtomicBoolean()
    private val stopEnable = AtomicBoolean()
    private val destroyEnable = AtomicBoolean()

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        if (createEnable.compareAndSet(true, false)) {
            onLoadedView()
            this.onCreate.invoke()
            startEnable.lazySet(true)
            resumeEnable.lazySet(false)
            pauseEnable.lazySet(false)
            stopEnable.lazySet(false)
            destroyEnable.lazySet(true)
        }
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (startEnable.compareAndSet(true, false)) {
            onCreate()
            this.onStart.invoke()
            createEnable.lazySet(false)
            resumeEnable.lazySet(true)
            pauseEnable.lazySet(false)
            stopEnable.lazySet(true)
            destroyEnable.lazySet(false)
        }
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        if (resumeEnable.compareAndSet(true, false)) {
            onStart()
            this.onResume.invoke()
            createEnable.lazySet(false)
            startEnable.lazySet(false)
            pauseEnable.lazySet(true)
            stopEnable.lazySet(false)
            destroyEnable.lazySet(false)
        }
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        if (pauseEnable.compareAndSet(true, false)) {
            onResume()
            this.onPause.invoke()
            createEnable.lazySet(false)
            startEnable.lazySet(false)
            resumeEnable.lazySet(true)
            stopEnable.lazySet(true)
            destroyEnable.lazySet(false)
        }
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        if (stopEnable.compareAndSet(true, false)) {
            onPause()
            this.onStop.invoke()
            createEnable.lazySet(false)
            startEnable.lazySet(true)
            resumeEnable.lazySet(false)
            pauseEnable.lazySet(false)
            destroyEnable.lazySet(true)
        }
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        if (destroyEnable.compareAndSet(true, false)) {
            onStop()
            this.onDestroy.invoke()
            createEnable.lazySet(true)
            startEnable.lazySet(false)
            resumeEnable.lazySet(false)
            pauseEnable.lazySet(false)
            stopEnable.lazySet(false)
        }
    }
}