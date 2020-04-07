package com.sjianjun.reader.module.main.fragment

import android.os.Bundle
import android.view.View
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.R

class BookReaderFragment : BaseFragment() {
    override fun getLayoutRes() = R.layout.main_fragment_book_reaader

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.supportActionBar?.hide()
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.supportActionBar?.show()
    }
}