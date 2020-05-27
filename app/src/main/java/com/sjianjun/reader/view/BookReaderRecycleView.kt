package com.sjianjun.reader.view

import android.content.Context
import android.graphics.Rect
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


//    override fun smoothScrollBy(dx: Int, dy: Int) {
//        Log.e("smoothScrollBy dx $dx dy $dy")
//        super.smoothScrollBy(dx, dy)
//    }

    class LayoutManager(context: Context?) : LinearLayoutManager(context) {

        override fun requestChildRectangleOnScreen(
            parent: RecyclerView,
            child: View,
            rect: Rect,
            immediate: Boolean,
            focusedChildVisible: Boolean
        ): Boolean {
            return true
        }

    }

}