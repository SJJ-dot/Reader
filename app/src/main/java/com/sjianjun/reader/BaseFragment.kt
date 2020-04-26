package com.sjianjun.reader

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.reader.utils.handler
import kotlinx.coroutines.*
import sjj.alog.Log
import kotlin.coroutines.CoroutineContext

open class BaseFragment : DialogFragment() {

    var onBackPressed: (() -> Unit)? = null
        set(value) {
            field = value
            activity?.onBackPressedDispatcher?.addCallback(owner = viewLifecycleOwner) {
                onBackPressed?.invoke()
            }
        }

    val activity: BaseActivity?
        get() = super.getActivity() as? BaseActivity

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), R.style.dialog_style)
    }

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

    fun <T> LiveData<T>.observeViewLifecycle(observer: (data: T) -> Unit) {
        observeViewLifecycle(Observer { observer(it) })
    }

    fun <T> LiveData<T>.observeViewLifecycle(observer: Observer<T>) {
        observe(viewLifecycleOwner, observer)
    }

    fun launch(
        context: CoroutineContext = handler,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return viewLifecycleOwner.lifecycleScope.launch(context, start, block)
    }

    fun launchIo(
        context: CoroutineContext = Dispatchers.IO + handler,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return launch(context, start, block)
    }

    private var snackbar: Snackbar? = null

    @SuppressLint("WrongConstant")
    fun showSnackbar(view: View?, msg: String, duration: Int = Snackbar.LENGTH_SHORT) {
        Log.i(msg)
        if (snackbar == null) {
            snackbar = Snackbar.make(view ?: return, msg, duration)
        } else {
            snackbar?.setText(msg)
            snackbar?.duration = duration
        }
        snackbar?.show()
    }

    fun newSnackbar(view: View?, msg: String, duration: Int = Snackbar.LENGTH_SHORT) {
        Log.i(msg)
        snackbar = Snackbar.make(view ?: return, msg, duration)
        snackbar?.show()
    }

    fun dismissSnackbar() {
        snackbar?.dismiss()
        snackbar = null
    }
}