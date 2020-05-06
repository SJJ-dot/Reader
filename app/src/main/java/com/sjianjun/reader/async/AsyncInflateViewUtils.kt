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


fun AsyncInflateRequest.inflateWithLoading(
    context: Context,
    onLoadedView: (View) -> Unit = this.onLoadedView
): View {
    return inflateWithLoading(LayoutInflater.from(context), onLoadedView)
}

fun AsyncInflateRequest.inflateWithLoading(
    inflater: LayoutInflater,
    onLoadedView: (View) -> Unit = this.onLoadedView
): View {
    val loadingView = AsyncLoadView(inflater.context)
    val parent = parent
    val attachToRoot = attachToRoot && parent != null
    if (attachToRoot) {
        parent!!.addView(loadingView, MATCH_PARENT, MATCH_PARENT)
    }

    inflate(inflater) {
        loadingView.hide()
        if (attachToRoot) {
            parent!!.removeView(loadingView)
            onLoadedView(parent)
        } else {
            loadingView.addView(it, MATCH_PARENT, MATCH_PARENT)
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


