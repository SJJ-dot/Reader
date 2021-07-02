package com.sjianjun.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sjianjun.async.AsyncView

abstract class BaseAsyncFragment : BaseFragment() {

    @Deprecated(
        "should use onLoadedView field or onCreate field",
        ReplaceWith("override val onLoadedView = ...")
    )
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val res = getLayoutRes()
        if (BuildConfig.DEBUG && res == 0) {
            error("not set layout res")
        }
        return AsyncView(requireContext(),res,callback = onLoadedView)
    }


    abstract override fun getLayoutRes(): Int

    open val onLoadedView: (View) -> Unit = {}
}