package com.sjianjun.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sjianjun.reader.async.createAsyncLoadView

abstract class BaseAsyncFragment : BaseFragment() {
    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val res = getLayoutRes()
        assert(res != 0) { "not set layout res" }
        return createAsyncLoadView(
            res, container, false, inflater,
            onLoadedView = onLoadedView,
            onCreate = onCreate,
            onStart = onStart,
            onResume = onResume,
            onPause = onPause,
            onStop = onStop,
            onDestroy = onDestroy
        )
    }

    abstract override fun getLayoutRes(): Int

    open val onLoadedView: BaseAsyncFragment.(View) -> Unit = {}
    open val onCreate: BaseAsyncFragment.() -> Unit = {}
    open val onStart: BaseAsyncFragment.() -> Unit = {}
    open val onResume: BaseAsyncFragment.() -> Unit = {}
    open val onPause: BaseAsyncFragment.() -> Unit = {}
    open val onStop: BaseAsyncFragment.() -> Unit = {}
    open val onDestroy: BaseAsyncFragment.() -> Unit = {}

    @Deprecated("should use onLoadedView field or onCreate field", ReplaceWith("override val onLoadedView = ..."))
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