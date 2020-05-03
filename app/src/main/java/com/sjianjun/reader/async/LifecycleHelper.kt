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

class LifecycleHelper<T>(private val activity: T,
                         private val onLoadedView: T.() -> Unit = {},
                         private val onCreate: T.() -> Unit = {},
                         private val onStart: T.() -> Unit = {},
                         private val onResume: T.() -> Unit = {},
                         private val onPause: T.() -> Unit = {},
                         private val onStop: T.() -> Unit = {},
                         private val onDestroy: T.() -> Unit = {}) : LifecycleObserver {

    private val createEnable = AtomicBoolean(true)
    private val startEnable = AtomicBoolean()
    private val resumeEnable = AtomicBoolean()
    private val pauseEnable = AtomicBoolean()
    private val stopEnable = AtomicBoolean()
    private val destroyEnable = AtomicBoolean()

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        if (createEnable.compareAndSet(true, false)) {
            onLoadedView(activity)
            logger.i("onCreate $activity")
            this.onCreate.invoke(activity)
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
            logger.i("onStart $activity")
            this.onStart.invoke(activity)
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
            logger.i("onResume $activity")
            this.onResume.invoke(activity)
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
            logger.i("onPause $activity")
            this.onPause.invoke(activity)
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
            logger.i("onStop $activity")
            this.onStop.invoke(activity)
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
            logger.i("onDestroy $activity")
            this.onDestroy.invoke(activity)
            createEnable.lazySet(true)
            startEnable.lazySet(false)
            resumeEnable.lazySet(false)
            pauseEnable.lazySet(false)
            stopEnable.lazySet(false)
        }
    }
}