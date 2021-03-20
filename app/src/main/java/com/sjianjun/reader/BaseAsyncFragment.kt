package com.sjianjun.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sjianjun.async.Disposable
import com.sjianjun.async.utils.AsyncInflateUtil
import com.sjianjun.reader.async.*

abstract class BaseAsyncFragment : BaseFragment() {
    private var disposable: Disposable? = null

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
        val result = AsyncInflateUtil().inflate(
            requireContext(),
            res,
            OnInflateFinishedResumeListener(lifecycle) { view: View, _: Int, parent: ViewGroup ->
                onLoadedView(view)
            })

        disposable = result.second
        return result.first
    }


    abstract override fun getLayoutRes(): Int

    open val onLoadedView: (View) -> Unit = {}


    override fun onDestroyView() {
        super.onDestroyView()
        disposable?.dispose()
    }

}