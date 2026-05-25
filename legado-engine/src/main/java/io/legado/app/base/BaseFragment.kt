package io.legado.app.base

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.ui.widget.TitleBar

@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseFragment(@LayoutRes layoutID: Int) : Fragment(layoutID) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onMultiWindowModeChanged()
        onFragmentCreated(view, savedInstanceState)
    }

    abstract fun onFragmentCreated(view: View, savedInstanceState: Bundle?)

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        onMultiWindowModeChanged()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onMultiWindowModeChanged()
    }

    private fun onMultiWindowModeChanged() {
        (activity as? BaseActivity<*>)?.let {
            view?.findViewById<TitleBar>(R.id.title_bar)
                ?.onMultiWindowModeChanged(it.isInMultiWindow, it.fullScreen)
        }
    }



}
