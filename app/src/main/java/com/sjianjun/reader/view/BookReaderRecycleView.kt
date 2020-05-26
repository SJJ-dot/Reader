package com.sjianjun.reader.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BookReaderRecycleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    init {
        layoutManager = LayoutManager(context)
    }

    override fun requestChildFocus(child: View?, focused: View?) {
    }

    class LayoutManager(context: Context?) : LinearLayoutManager(context) {
    }

}