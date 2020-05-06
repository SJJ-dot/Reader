package com.sjianjun.reader

import android.os.Bundle
import android.view.View
import com.sjianjun.reader.async.asyncInflateRequest
import com.sjianjun.reader.async.inflateWithLoading

abstract class BaseAsyncActivity : BaseActivity() {
    @Deprecated("should use layoutRes and onLoadedView field", ReplaceWith("override val onLoadedView = ..."))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = asyncInflateRequest(layoutRes).apply {
            onLoadedView = this@BaseAsyncActivity.onLoadedView
            onCreate = this@BaseAsyncActivity.onCreate
            onStart = this@BaseAsyncActivity.onStart
            onResume = this@BaseAsyncActivity.onResume
            onPause = this@BaseAsyncActivity.onPause
            onStop = this@BaseAsyncActivity.onStop
            onDestroy = this@BaseAsyncActivity.onDestroy
        }.inflateWithLoading(this, dispatchState)
        setContentView(view)
    }

    open val dispatchState = false

    abstract val layoutRes: Int
    open val onLoadedView: (View) -> Unit = {}
    open val onCreate: () -> Unit = {}
    open val onStart: () -> Unit = {}
    open val onResume: () -> Unit = {}
    open val onPause: () -> Unit = {}
    open val onStop: () -> Unit = {}
    open val onDestroy: () -> Unit = {}

    @Deprecated("should use onStart field", ReplaceWith("override val onStart = ..."))
    override fun onStart() {
        super.onStart()
    }

    @Deprecated("should use onResume field", ReplaceWith("override val onResume = ..."))
    override fun onResume() {
        super.onResume()
    }

    @Deprecated("should use onPause field", ReplaceWith("override val onPause = ..."))
    override fun onPause() {
        super.onPause()
    }

    @Deprecated("should use onStop field", ReplaceWith("override val onStop = ..."))
    override fun onStop() {
        super.onStop()
    }

    @Deprecated("should use onDestroy field", ReplaceWith("override val onDestroy = ..."))
    override fun onDestroy() {
        super.onDestroy()
    }
}