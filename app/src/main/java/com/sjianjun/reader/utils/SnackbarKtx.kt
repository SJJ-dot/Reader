package com.sjianjun.reader.utils

import android.view.View
import androidx.annotation.UiThread
import com.google.android.material.snackbar.Snackbar
import sjj.alog.Log

@UiThread
fun showSnackbar(view: View?, msg: String, duration: Int = Snackbar.LENGTH_SHORT): Snackbar? {
    Log.i(msg)
    val snackbar = Snackbar.make(view ?: return null, msg, duration)
    snackbar.show()
    return snackbar
}