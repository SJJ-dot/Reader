package com.sjianjun.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sjianjun.reader.async.*

abstract class BaseAsyncFragment : BaseFragment() {
    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val res = getLayoutRes()
        assert(res != 0) { "not set layout res" }
        return asyncInflateRequest(res).apply {
            onLoadedView = this@BaseAsyncFragment.onLoadedView
            onCreate = this@BaseAsyncFragment.onCreate
            onStart = this@BaseAsyncFragment.onStart
            onResume = this@BaseAsyncFragment.onResume
            onPause = this@BaseAsyncFragment.onPause
            onStop = this@BaseAsyncFragment.onStop
            onDestroy = this@BaseAsyncFragment.onDestroy
            applyAsyncInflateRequest()
        }.inflateWithLoading(inflater, dispatchState)
    }

    open val applyAsyncInflateRequest: AsyncInflateRequest.() -> Unit = {}

    open val dispatchState = false

    abstract override fun getLayoutRes(): Int

    open val onLoadedView: (View) -> Unit = emptyLoad
    open val onCreate: () -> Unit = empty
    open val onStart: () -> Unit = empty
    open val onResume: () -> Unit = empty
    open val onPause: () -> Unit = empty
    open val onStop: () -> Unit = empty
    open val onDestroy: () -> Unit = empty

    @Deprecated(
        "should use onLoadedView field or onCreate field",
        ReplaceWith("override val onLoadedView = ...")
    )
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    @Deprecated("should use onCreate field", ReplaceWith("override val onCreate = ..."))
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
    override fun onDestroyView() {
        super.onDestroyView()
    }

}