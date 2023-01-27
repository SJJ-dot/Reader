package com.sjianjun.reader.utils

import android.view.View
import androidx.annotation.UiThread
import com.google.android.material.snackbar.Snackbar
import com.sjianjun.reader.R
import sjj.alog.Log

@UiThread
fun showSnackbar(view: View?, msg: String, duration: Int = Snackbar.LENGTH_SHORT): Snackbar? {
    Log.i(msg)
    var snackbar = view?.getTag(R.id.snackbar) as? Snackbar?
    if (snackbar?.isShown == true) {
        snackbar.setText(msg)
        snackbar.show()
        return snackbar
    }
    snackbar = Snackbar.make(view ?: return null, msg, duration)
    view.setTag(R.id.snackbar,snackbar)
    snackbar.show()
    return snackbar
}