package com.sjianjun.reader.async

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.sjianjun.reader.utils.animFadeIn
import sjj.alog.Log

private const val ANIM_TIME = 500L

fun <T> T.inflateWithAsync(

    context: Context,
    lifecycle: Lifecycle,
    @LayoutRes layoutRes: Int,
    parent: ViewGroup? = null,
    attachToRoot: Boolean = parent != null,
    inflater: LayoutInflater? = null,
    onLoadedView: T.(View) -> Unit = {},
    onCreate: T.() -> Unit = {},
    onStart: T.() -> Unit = {},
    onResume: T.() -> Unit = {},
    onPause: T.() -> Unit = {},
    onStop: T.() -> Unit = {},
    onDestroy: T.() -> Unit = {}
) {

    AsyncLayoutInflater(context, inflater).inflate(
        layoutRes,
        parent
    ) { view: View, _: Int, _: ViewGroup? ->
        lifecycle.addObserver(LifecycleHelper(this, {
            Log.i("onLoadedView $this")
            if (attachToRoot && view.parent == null && parent != null) {
                parent.addView(view)
                onLoadedView(parent)
            } else {
                onLoadedView(view)
            }
            view.animFadeIn(ANIM_TIME)
        }, onCreate, onStart, onResume, onPause, onStop, onDestroy))
    }
}

fun <T : AppCompatActivity> T.inflateWithAsync(
    @LayoutRes layoutRes: Int,
    parent: ViewGroup? = null,
    attachToRoot: Boolean = true,
    onLoadedView: T.(View) -> Unit = {},
    onCreate: T.() -> Unit = {},
    onStart: T.() -> Unit = {},
    onResume: T.() -> Unit = {},
    onPause: T.() -> Unit = {},
    onStop: T.() -> Unit = {},
    onDestroy: T.() -> Unit = {}
) {
    inflateWithAsync(this, lifecycle, layoutRes, parent, attachToRoot, null, {
        if (attachToRoot) {
            setContentView(parent ?: it)
        }
        onLoadedView(parent ?: it)
    }, onCreate, onStart, onResume, onPause, onStop, onDestroy)
}

fun <T : AppCompatActivity> T.createAsyncLoadView(
    @LayoutRes layoutRes: Int,
    parent: ViewGroup? = null,
    attachToRoot: Boolean = true,
    onLoadedView: T.(View) -> Unit = {},
    onCreate: T.() -> Unit = {},
    onStart: T.() -> Unit = {},
    onResume: T.() -> Unit = {},
    onPause: T.() -> Unit = {},
    onStop: T.() -> Unit = {},
    onDestroy: T.() -> Unit = {}
): View {
    val loadingView = AsyncLoadView(this)
    loadingView.show()
    if (attachToRoot) {
        parent?.addView(loadingView, MATCH_PARENT, MATCH_PARENT)
    }
    inflateWithAsync(
        this,
        lifecycle,
        layoutRes,
        loadingView,
        true,
        null,
        {
            loadingView.hide()
            onLoadedView(it)
        },
        onCreate, onStart, onResume, onPause, onStop, onDestroy
    )

    return if (attachToRoot) parent ?: loadingView else loadingView
}

fun <T : Fragment> T.createAsyncLoadView(
    @LayoutRes layoutRes: Int,
    parent: ViewGroup? = null,
    attachToRoot: Boolean = false,
    inflater: LayoutInflater? = null,
    onLoadedView: T.(View) -> Unit = {},
    onCreate: T.() -> Unit = {},
    onStart: T.() -> Unit = {},
    onResume: T.() -> Unit = {},
    onPause: T.() -> Unit = {},
    onStop: T.() -> Unit = {},
    onDestroy: T.() -> Unit = {}
): View {
    val loadingView = AsyncLoadView(context!!)
    loadingView.show()
    if (attachToRoot) {
        parent?.addView(loadingView, MATCH_PARENT, MATCH_PARENT)
    }

    inflateWithAsync(
        context!!,
        viewLifecycleOwner.lifecycle,
        layoutRes,
        loadingView,
        true,
        inflater,
        {
            loadingView.hide()
            onLoadedView(it)
        },
        onCreate, onStart, onResume, onPause, onStop, onDestroy
    )
    return if (attachToRoot) parent ?: loadingView else loadingView
}

fun createAsyncLoadView(
    context: Context,
    @LayoutRes layoutRes: Int,
    parent: ViewGroup? = null,
    attachToRoot: Boolean = false,
    onLoadedView: (View) -> Unit = {}
) {
    AsyncLayoutInflater(context, null).inflate(
        layoutRes,
        parent
    ) { view: View, _: Int, _: ViewGroup? ->

        if (attachToRoot && view.parent == null && parent != null) {
            parent.addView(view)
            onLoadedView(parent)
        } else {
            onLoadedView(view)
        }
        view.animFadeIn(ANIM_TIME)
    }
}

fun ViewGroup?.createAsyncLoadView(
    context: Context,
    @LayoutRes layoutRes: Int,
    attachToRoot: Boolean = false,
    onLoadedView: (View) -> Unit = {}
) {
    createAsyncLoadView(context, layoutRes, this, attachToRoot, onLoadedView)
}

