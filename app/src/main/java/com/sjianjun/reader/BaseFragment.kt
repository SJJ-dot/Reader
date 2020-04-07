package com.sjianjun.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

open class BaseFragment : DialogFragment(), CoroutineScope by MainScope() {

    var onBackPressed: (() -> Unit)? = null
        set(value) {
            field = value
            activity?.onBackPressedDispatcher?.addCallback(owner = viewLifecycleOwner) {
                onBackPressed?.invoke()
            }
        }

    val activity: BaseActivity?
        get() = super.getActivity() as? BaseActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val res = getLayoutRes()
        if (res != 0) {
            return inflater.inflate(res, container, false)
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @LayoutRes
    open fun getLayoutRes(): Int = 0

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    fun <T> LiveData<T>.observeViewLifecycle(observer: (data: T) -> Unit) {
        observeViewLifecycle(Observer { observer(it) })
    }

    fun <T> LiveData<T>.observeViewLifecycle(observer: Observer<T>) {
        observe(viewLifecycleOwner, observer)
    }

    fun viewLaunch(context: CoroutineContext = EmptyCoroutineContext,
                   start: CoroutineStart = CoroutineStart.DEFAULT,
                   block: suspend CoroutineScope.() -> Unit): Job {
        return viewLifecycleOwner.lifecycle.coroutineScope.launch(context, start, block)
    }
}