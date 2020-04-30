package com.sjianjun.reader.async

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import sjj.alog.Log
import java.util.concurrent.atomic.AtomicBoolean

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
            Log.i("onCreate")
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
            Log.i("onStart")
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
            Log.i("onResume")
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
            Log.i("onPause")
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
            Log.i("onStop")
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
            Log.i("onDestroy")
            this.onDestroy.invoke(activity)
            createEnable.lazySet(true)
            startEnable.lazySet(false)
            resumeEnable.lazySet(false)
            pauseEnable.lazySet(false)
            stopEnable.lazySet(false)
        }
    }
}