package com.sjianjun.reader.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.reader.R
import sjj.alog.Log

fun View?.showSnackbar(
    msg: String, duration: Int = Snackbar.LENGTH_SHORT,
    actionText: String = "确认", onConfirm: (() -> Unit)? = null
): Snackbar? {
    Log.i(msg)
    this ?: return null
    var snackbar = getTag(R.id.snackbar) as? Snackbar?
    if (snackbar?.isShown == true) {
        if (onConfirm != null) {
            snackbar.setAction(actionText) { onConfirm() }
        }
        snackbar.setText(msg)
        snackbar.show()
        return snackbar
    }
    snackbar = Snackbar.make(this, msg, duration)
    setTag(R.id.snackbar, snackbar)
    if (onConfirm != null) {
        snackbar.setAction(actionText) { onConfirm() }
    }
    snackbar.show()
    return snackbar
}