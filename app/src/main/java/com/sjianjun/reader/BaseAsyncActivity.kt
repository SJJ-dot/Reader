package com.sjianjun.reader

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.sjianjun.async.Disposable
import com.sjianjun.async.utils.AsyncInflateUtil
import com.sjianjun.reader.async.*

abstract class BaseAsyncActivity : BaseActivity() {

    private var disposable: Disposable? = null

    @Deprecated(
        "should use layoutRes and onLoadedView field",
        ReplaceWith("override val onLoadedView = ...")
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isFinishing) {
            val asyncInflateUtil = AsyncInflateUtil()
            asyncInflateUtil.config.fadeOut = fadeOut
            val result = asyncInflateUtil.inflate(
                this,
                layoutRes,
                OnInflateFinishedResumeListener(lifecycle) { view: View, _: Int, parent: ViewGroup ->
                    onLoadedView(view)
                })
            disposable = result.second
            setContentView(result.first)
        }
    }

    open val fadeOut: Boolean = true
    abstract val layoutRes: Int
    open val onLoadedView: (View) -> Unit = {}

    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
    }
}