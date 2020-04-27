package com.sjianjun.reader.utils

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.sjianjun.reader.R

fun View.show() {
    visibility(View.VISIBLE, this)
}

fun View.hide() {
    visibility(View.INVISIBLE, this)
}

fun View.gone() {
    visibility(View.GONE, this)
}

fun visibility(visibility: Int, vararg viewList: View) {
    val view = viewList.firstOrNull() ?: return
    val parent = view.parent
    if (parent is ConstraintLayout) {
        var tag = parent.getTag(R.id.visibility) as? ConstraintSet
        if (tag == null) {
            tag = ConstraintSet()
            parent.setTag(R.id.visibility, tag)
        }
        tag.clone(parent)
        viewList.forEach {
            tag.setVisibility(it.id, visibility)
        }
        tag.applyTo(parent)
    }
    view.visibility = visibility
}