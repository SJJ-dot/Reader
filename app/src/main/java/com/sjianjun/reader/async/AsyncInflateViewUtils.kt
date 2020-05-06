package com.sjianjun.reader.async

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment


fun AsyncInflateRequest.inflateWithLoading(
    context: Context,
    dispatchState: Boolean = false,
    onLoadedView: (View) -> Unit = this.onLoadedView
): View {
    return inflateWithLoading(LayoutInflater.from(context),dispatchState, onLoadedView)
}

fun AsyncInflateRequest.inflateWithLoading(
    inflater: LayoutInflater,
    dispatchState: Boolean = false,
    onLoadedView: (View) -> Unit = this.onLoadedView
): View {
    val loadingView = AsyncLoadView(inflater.context)
    val parent = parent
    val attachToRoot = attachToRoot && parent != null
    if (attachToRoot) {
        parent!!.addView(loadingView, MATCH_PARENT, MATCH_PARENT)
    }
    this.parent = loadingView
    this.attachToRoot = false

    inflate(inflater) {
        loadingView.hide()
        loadingView.setContentView(it, dispatchState)
        if (attachToRoot) {
            onLoadedView(parent!!)
        } else {
            onLoadedView(loadingView)
        }
    }
    return if (attachToRoot) {
        parent!!
    } else {
        loadingView
    }
}

fun AppCompatActivity.asyncInflateRequest(
    @LayoutRes layoutRes: Int,
    parent: ViewGroup? = null,
    attachToRoot: Boolean = parent != null
): AsyncInflateRequest {
    val asyncInflateRequest = AsyncInflateRequest(layoutRes, parent, attachToRoot)
    asyncInflateRequest.lifecycle = lifecycle
    return asyncInflateRequest
}

fun Fragment.asyncInflateRequest(
    @LayoutRes layoutRes: Int,
    parent: ViewGroup? = null,
    attachToRoot: Boolean = parent != null
): AsyncInflateRequest {
    val asyncInflateRequest = AsyncInflateRequest(layoutRes, parent, attachToRoot)
    asyncInflateRequest.lifecycle = viewLifecycleOwner.lifecycle
    return asyncInflateRequest
}


