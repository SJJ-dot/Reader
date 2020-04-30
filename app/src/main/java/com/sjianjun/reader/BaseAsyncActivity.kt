package com.sjianjun.reader

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.coroutineScope
import com.gyf.immersionbar.ImmersionBar
import com.sjianjun.reader.async.inflateWithAsync
import com.sjianjun.reader.utils.handler
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

abstract class BaseAsyncActivity : BaseActivity() {
    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inflateWithAsync(
            layoutRes,
            null,
            true,
            onLoadedView,
            onCreate,
            onStart,
            onResume,
            onPause,
            onStop,
            onDestroy
        )
    }

    abstract val layoutRes: Int
    open val onLoadedView: BaseAsyncActivity.(View) -> Unit = {}
    open val onCreate: BaseAsyncActivity.() -> Unit = {}
    open val onStart: BaseAsyncActivity.() -> Unit = {}
    open val onResume: BaseAsyncActivity.() -> Unit = {}
    open val onPause: BaseAsyncActivity.() -> Unit = {}
    open val onStop: BaseAsyncActivity.() -> Unit = {}
    open val onDestroy: BaseAsyncActivity.() -> Unit = {}

    @Deprecated("should use or onStart field", ReplaceWith("override val onStart = ..."))
    override fun onStart() {
        super.onStart()
    }

    @Deprecated("should use or onResume field", ReplaceWith("override val onResume = ..."))
    override fun onResume() {
        super.onResume()
    }

    @Deprecated("should use or onPause field", ReplaceWith("override val onPause = ..."))
    override fun onPause() {
        super.onPause()
    }

    @Deprecated("should use or onStop field", ReplaceWith("override val onStop = ..."))
    override fun onStop() {
        super.onStop()
    }

    @Deprecated("should use or onDestroy field", ReplaceWith("override val onDestroy = ..."))
    override fun onDestroy() {
        super.onDestroy()
    }
}