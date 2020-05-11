package com.sjianjun.reader.async

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.lifecycle.Lifecycle
import com.sjianjun.reader.utils.animFadeIn
import sjj.alog.Logger

val empty = {}
val emptyLoad = { _: View -> Unit }

class AsyncInflateRequest(
    @LayoutRes var layoutRes: Int,
    var parent: ViewGroup? = null,
    var attachToRoot: Boolean = parent != null
) {

    var logger: Logger? = null

    var animTime = 500L

    var lifecycle: Lifecycle? = null

    var onLoadedView: (View) -> Unit = emptyLoad
    var onStart: () -> Unit = empty
    var onResume: () -> Unit = empty
    var onPause: () -> Unit = empty
    var onStop: () -> Unit = empty

    fun inflate(context: Context, onLoadedView: (View) -> Unit = this.onLoadedView) {
        inflate(LayoutInflater.from(context), onLoadedView)
    }

    fun inflate(inflater: LayoutInflater, onLoadedView: (View) -> Unit = this.onLoadedView) {

        val parent = parent
        val attachToRoot = attachToRoot && parent != null

        AsyncLayoutInflater(inflater, logger).inflate(
            layoutRes,
            parent
        ) { view: View, _: Int, _: ViewGroup? ->

            val onViewLoaded = {
                if (attachToRoot && view.parent == null) {
                    parent!!.addView(view)
                    onLoadedView(parent)
                } else {
                    onLoadedView(view)
                }
                view.animFadeIn(animTime)
            }

            val lifecycle = lifecycle
            if (lifecycle == null) {
                onViewLoaded()
            } else {
                lifecycle.addObserver(
                    LifecycleHelper(
                        logger,
                        onViewLoaded,
                        onStart,
                        onResume,
                        onPause,
                        onStop
                    )
                )
            }
        }
    }


}