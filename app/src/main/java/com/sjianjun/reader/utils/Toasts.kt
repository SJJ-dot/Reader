package com.sjianjun.reader.utils

import android.widget.Toast
import com.sjianjun.reader.App

fun show(msg: String?) {
    if (msg != null) {
        launchMain {
            Toast.makeText(App.app, msg, Toast.LENGTH_SHORT).show()
        }
    }
}