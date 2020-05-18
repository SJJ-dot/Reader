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

fun ConstraintLayout?.visibleSet(): VisibleSet {
    if (this == null) {
        return VisibleSet(ConstraintSet(), null)
    }
    var tag = getTag(R.id.visibility) as? ConstraintSet
    if (tag == null) {
        tag = ConstraintSet()
        setTag(R.id.visibility, tag)
    }
    tag.clone(this)
    return VisibleSet(tag, this)
}

class VisibleSet(val constraintSet: ConstraintSet, var constraintLayout: ConstraintLayout?) {
    fun visible(vararg view: View): VisibleSet {
        return visible(*view.map { it.id }.toIntArray())
    }

    fun invisible(vararg view: View): VisibleSet {
        return invisible(*view.map { it.id }.toIntArray())
    }

    fun gone(vararg view: View): VisibleSet {
        return gone(*view.map { it.id }.toIntArray())
    }

    fun visible(vararg viewId: Int): VisibleSet {
        visibility(View.VISIBLE, *viewId)
        return this
    }

    fun invisible(vararg viewId: Int): VisibleSet {
        visibility(View.INVISIBLE, *viewId)
        return this
    }

    fun gone(vararg viewId: Int): VisibleSet {
        visibility(View.GONE, *viewId)
        return this
    }

    private fun visibility(visibility: Int, vararg viewId: Int) {
        viewId.forEach {
            constraintSet.setVisibility(it, visibility)
        }
    }

    fun apply() {
        constraintSet.applyTo(constraintLayout ?: return)
    }
}