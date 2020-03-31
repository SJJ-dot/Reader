package com.sjianjun.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import sjj.alog.Log

open class BaseFragment : DialogFragment(), CoroutineScope by MainScope() {
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

    override fun onDestroyView() {
        super.onDestroyView()
        cancel()
    }
}